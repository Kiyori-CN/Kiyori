package com.ai.assistance.operit.ui.features.chat.components

import java.util.Locale

internal object ChatStatisticsFormatter {
    fun formatCacheHitRate(rate: Double?): String? =
        rate?.let { String.format(Locale.US, "%.1f%%", it * 100.0) }

    fun formatCoverage(reported: Int, total: Int): String = "$reported/$total"
}
