package com.ai.assistance.operit.data.preferences

import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechServicesDefaultProfileTest {
    @Test
    fun `next preset carries the verified request contract`() {
        val config = SpeechServicesPreferences.DEFAULT_NEXT_TTS_PRESET

        assertEquals("GET", config.httpMethod)
        assertEquals("audio/mpeg", config.contentType)
        assertEquals("zh-CN-XiaoxiaoNeural", config.voiceId)
        assertEquals(
            SpeechServicesPreferences.HttpTtsRateParameterMode.OFFSET_PERCENT,
            config.rateParameterMode,
        )
        assertTrue(config.urlTemplate.contains("t={text}"))
        assertTrue(config.urlTemplate.contains("v={voice}"))
        assertTrue(config.urlTemplate.contains("r={rate}"))
        assertFalse(config.urlTemplate.contains("api_key={apiKey}"))
    }

    @Test
    fun `new install chooses next while legacy settings choose migrated profile`() {
        val legacy = sampleProfile("legacy")
        val next = sampleProfile(SpeechServicesPreferences.DEFAULT_NEXT_TTS_PROFILE_ID)

        assertEquals(next, SpeechServiceProfilesPreferences.chooseInitialTtsProfile(false, legacy, next))
        assertEquals(legacy, SpeechServiceProfilesPreferences.chooseInitialTtsProfile(true, legacy, next))
    }

    private fun sampleProfile(id: String): SpeechServiceProfilesPreferences.TtsProfile =
        SpeechServiceProfilesPreferences.TtsProfile(
            id = id,
            name = id,
            serviceType = VoiceServiceFactory.VoiceServiceType.HTTP_TTS,
            httpConfig = SpeechServicesPreferences.DEFAULT_NEXT_TTS_PRESET,
            vitsConfig = SpeechServicesPreferences.DEFAULT_VITS_TTS_PACKAGE_CONFIG,
            cleanerRegexs = emptyList(),
            speechRate = 1.0f,
            pitch = 1.0f,
            createdAt = 1L,
            updatedAt = 1L,
        )
}
