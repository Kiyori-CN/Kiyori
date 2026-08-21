package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import com.ai.assistance.operit.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingQualityPresentationTest {
    @Test
    fun fiveQualityLevelsUseTheSharedSemanticLabels() {
        assertEquals(
            listOf(
                R.string.thinking_quality_low,
                R.string.thinking_quality_medium,
                R.string.thinking_quality_high,
                R.string.thinking_quality_xhigh,
                R.string.thinking_quality_max,
            ),
            (1..5).map(::thinkingQualityLabelRes),
        )
    }

    @Test
    fun outOfRangeLevelsAreClampedToTheVisibleFiveLevelContract() {
        assertEquals(R.string.thinking_quality_low, thinkingQualityLabelRes(Int.MIN_VALUE))
        assertEquals(R.string.thinking_quality_max, thinkingQualityLabelRes(Int.MAX_VALUE))
    }
}
