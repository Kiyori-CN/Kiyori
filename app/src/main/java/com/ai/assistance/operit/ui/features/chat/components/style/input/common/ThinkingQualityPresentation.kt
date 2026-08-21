package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import androidx.annotation.StringRes
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences

@StringRes
internal fun thinkingQualityLabelRes(level: Int): Int {
    return when (
        level.coerceIn(
            ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
            ApiPreferences.MAX_THINKING_QUALITY_LEVEL,
        )
    ) {
        1 -> R.string.thinking_quality_low
        2 -> R.string.thinking_quality_medium
        3 -> R.string.thinking_quality_high
        4 -> R.string.thinking_quality_xhigh
        else -> R.string.thinking_quality_max
    }
}
