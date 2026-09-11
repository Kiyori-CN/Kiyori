package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

internal data class FileManagerScrollThumb(val top: Float, val height: Float)

/** 可滚动范围使用 Float 相减防止 Int 溢出；触达首尾时吸附轨道端点，避免估算误差留缝。 */
internal fun fileManagerScrollThumb(
    content: Int, viewport: Int, offset: Int, track: Float, thumbLength: Float,
    canScrollBackward: Boolean, canScrollForward: Boolean,
): FileManagerScrollThumb? {
    if ((!canScrollBackward && !canScrollForward) || content <= 0 || viewport <= 0 || offset < 0 ||
        !track.isFinite() || track <= 0 || !thumbLength.isFinite() || thumbLength <= 0
    ) return null
    // 不等高行的总长度会随测量变化；它只决定位置，不能让滑块在滚动中伸缩。
    val height = thumbLength.coerceAtMost(track)
    val fraction = when {
        !canScrollBackward -> 0f
        !canScrollForward -> 1f
        else -> (offset / (content.toFloat() - viewport).coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    return FileManagerScrollThumb((track - height) * fraction, height)
}
