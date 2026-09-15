package com.ai.assistance.operit.ui.features.chat.components

import java.util.Locale
import kotlin.math.roundToInt

internal object ChatStatisticsFormatter {
    fun contextUsageRatio(currentTokens: Long, maxTokens: Long): Double? =
        if (currentTokens < 0L || maxTokens <= 0L) null else currentTokens.toDouble() / maxTokens

    fun formatContextUsage(currentTokens: Long, maxTokens: Long): String? =
        contextUsageRatio(currentTokens, maxTokens)?.let { String.format(Locale.US, "%.1f%%", it * 100.0) }

    /**
     * 顶栏入口只有一圈进度可用，必须一眼读完：取整到个位，不显示小数。
     * 面板里仍然给一位小数，两处精度差异是刻意的。
     */
    fun formatContextUsageCompact(currentTokens: Long, maxTokens: Long): String? =
        contextUsageRatio(currentTokens, maxTokens)
            ?.let { String.format(Locale.US, "%d%%", (it * 100.0).roundToInt().coerceIn(0, 999)) }

    fun formatGenerationSpeed(tokensPerSecond: Double?): String? =
        tokensPerSecond?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { String.format(Locale.US, "%.1f", it) }

    fun formatCacheHitRate(rate: Double?): String? =
        rate?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?.let { String.format(Locale.US, "%.1f%%", it * 100.0) }

    fun formatDuration(milliseconds: Long?): String? = milliseconds?.takeIf { it >= 0L }
        ?.let { String.format(Locale.US, "%.2f s", it.toDouble() / 1000.0) }

    fun formatCoverage(reported: Int, total: Int): String = "$reported/$total"
}
