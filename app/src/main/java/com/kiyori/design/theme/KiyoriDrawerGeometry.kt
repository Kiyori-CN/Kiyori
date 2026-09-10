package com.kiyori.design.theme

/** AI 与文件存储抽屉共用宽度；不依赖任何页面导航状态。 */
fun calculateKiyoriDrawerWidthDp(windowWidthDp: Float, separatingFoldLeftDp: Float? = null): Float {
    require(windowWidthDp > 0f) { "windowWidthDp must be positive" }
    val width = when {
        windowWidthDp < 600f -> windowWidthDp * 0.75f
        windowWidthDp < 840f -> 320f
        else -> 360f
    }
    return separatingFoldLeftDp?.takeIf { it > 0f }?.let { minOf(width, it) } ?: width
}
