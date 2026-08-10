package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import kotlin.math.abs
import kotlin.math.roundToInt

internal data class BrowserViewportSize(
    val width: Int,
    val height: Int,
)

internal data class BrowserViewportMetrics(
    val innerWidth: Int,
    val innerHeight: Int,
    val clientWidth: Int,
    val clientHeight: Int,
)

internal object BrowserViewportPolicy {
    fun requestedSize(width: Int, height: Int): BrowserViewportSize {
        require(width > 0 && height > 0) {
            "Browser viewport width and height must be positive"
        }
        return BrowserViewportSize(width = width, height = height)
    }

    fun hostSize(
        requestedWidth: Int?,
        requestedHeight: Int?,
        defaultWidth: Int,
        defaultHeight: Int,
    ): BrowserViewportSize =
        if (requestedWidth != null && requestedHeight != null) {
            requestedSize(requestedWidth, requestedHeight)
        } else {
            requestedSize(defaultWidth, defaultHeight)
        }

    fun hostLayoutSize(
        requested: BrowserViewportSize,
        density: Float,
    ): BrowserViewportSize {
        require(density > 0f) {
            "Browser host density must be positive"
        }
        return BrowserViewportSize(
            width = (requested.width * density).roundToInt().coerceAtLeast(1),
            height = (requested.height * density).roundToInt().coerceAtLeast(1),
        )
    }

    fun mapCssCoordinate(
        cssCoordinate: Double,
        cssExtent: Double,
        viewExtent: Int,
    ): Int {
        require(cssExtent > 0.0 && viewExtent > 0) {
            "CSS and WebView extents must be positive"
        }
        return (cssCoordinate * viewExtent / cssExtent).roundToInt()
            .coerceIn(0, viewExtent)
    }

    fun matches(
        metrics: BrowserViewportMetrics,
        requested: BrowserViewportSize,
        tolerancePx: Int = 2,
    ): Boolean =
        metrics.innerWidth > 0 &&
            metrics.innerHeight > 0 &&
            metrics.clientWidth > 0 &&
            metrics.clientHeight > 0 &&
            abs(metrics.innerWidth - requested.width) <= tolerancePx &&
            abs(metrics.innerHeight - requested.height) <= tolerancePx &&
            abs(metrics.clientWidth - requested.width) <= tolerancePx &&
            abs(metrics.clientHeight - requested.height) <= tolerancePx
}
