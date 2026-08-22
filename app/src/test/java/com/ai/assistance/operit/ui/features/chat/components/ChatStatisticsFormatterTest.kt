package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStatisticsFormatterTest {
    @Test
    fun cacheHitRateUsesStableOneDecimalPercent() {
        assertEquals("73.3%", ChatStatisticsFormatter.formatCacheHitRate(110.0 / 150.0))
        assertEquals("0.0%", ChatStatisticsFormatter.formatCacheHitRate(0.0))
        assertEquals("100.0%", ChatStatisticsFormatter.formatCacheHitRate(1.0))
    }

    @Test
    fun missingCacheMetricRemainsUnknown() {
        assertNull(ChatStatisticsFormatter.formatCacheHitRate(null))
    }

    @Test
    fun coverageKeepsReportedAndTotalHopsDistinct() {
        assertEquals("2/5", ChatStatisticsFormatter.formatCoverage(2, 5))
    }
}
