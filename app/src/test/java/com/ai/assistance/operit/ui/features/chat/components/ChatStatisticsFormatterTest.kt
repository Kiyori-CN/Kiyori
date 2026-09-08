package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStatisticsFormatterTest {
    @Test
    fun contextPercentageKeepsPrecisionAndDoesNotHideOverLimit() {
        assertEquals("12.5%", ChatStatisticsFormatter.formatContextUsage(25523, 204800))
        assertEquals("0.0%", ChatStatisticsFormatter.formatContextUsage(0, 204800))
        assertEquals("125.0%", ChatStatisticsFormatter.formatContextUsage(256000, 204800))
        assertNull(ChatStatisticsFormatter.formatContextUsage(25523, 0))
        assertNull(ChatStatisticsFormatter.formatContextUsage(-1, 204800))
    }

    @Test
    fun generationSpeedRejectsInvalidMeasurements() {
        assertEquals("62.5", ChatStatisticsFormatter.formatGenerationSpeed(62.5))
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertNull(ChatStatisticsFormatter.formatGenerationSpeed(it))
        }
    }

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
