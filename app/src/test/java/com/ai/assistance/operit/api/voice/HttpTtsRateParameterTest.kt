package com.ai.assistance.operit.api.voice

import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpTtsRateParameterTest {
    @Test
    fun `direct mode preserves existing rate values`() {
        assertEquals(
            "1.25",
            formatHttpTtsRate(
                1.25f,
                SpeechServicesPreferences.HttpTtsRateParameterMode.DIRECT,
            ),
        )
    }

    @Test
    fun `next mode maps normal speed to zero`() {
        assertEquals(
            "0",
            formatHttpTtsRate(
                1.0f,
                SpeechServicesPreferences.HttpTtsRateParameterMode.OFFSET_PERCENT,
            ),
        )
    }

    @Test
    fun `next mode maps percentage changes around normal speed`() {
        val mode = SpeechServicesPreferences.HttpTtsRateParameterMode.OFFSET_PERCENT
        assertEquals("20", formatHttpTtsRate(1.2f, mode))
        assertEquals("-15", formatHttpTtsRate(0.85f, mode))
    }
}
