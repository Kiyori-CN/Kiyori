package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import kotlin.math.min

internal const val BROWSER_TAB_THUMBNAIL_WIDTH_PX = 320
internal const val BROWSER_TAB_THUMBNAIL_HEIGHT_PX = 512
internal const val BROWSER_TAB_THUMBNAIL_ASPECT_RATIO = 5f / 8f

internal data class BrowserThumbnailTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

internal fun resolveBrowserThumbnailTransform(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int = BROWSER_TAB_THUMBNAIL_WIDTH_PX,
    targetHeight: Int = BROWSER_TAB_THUMBNAIL_HEIGHT_PX,
): BrowserThumbnailTransform? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
        return null
    }
    val scale =
        min(
            targetWidth.toFloat() / sourceWidth.toFloat(),
            targetHeight.toFloat() / sourceHeight.toFloat(),
        )
    return BrowserThumbnailTransform(
        scale = scale,
        offsetX = (targetWidth - sourceWidth * scale) / 2f,
        offsetY = (targetHeight - sourceHeight * scale) / 2f,
    )
}
