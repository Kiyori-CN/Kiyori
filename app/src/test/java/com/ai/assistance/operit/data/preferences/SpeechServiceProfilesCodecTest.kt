package com.ai.assistance.operit.data.preferences

import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeechServiceProfilesCodecTest {
    @Test
    fun `round trips complete tts and stt profiles`() {
        val tts = SpeechServiceProfilesPreferences.TtsProfile(
            id = "tts-1",
            name = "Primary TTS",
            serviceType = VoiceServiceFactory.VoiceServiceType.OPENAI_TTS,
            httpConfig = SpeechServicesPreferences.TtsHttpConfig(
                urlTemplate = "https://example.test/tts",
                apiKey = "tts-test-key",
                headers = mapOf("X-Test" to "value"),
                localeTag = "zh-CN",
                voiceId = "voice",
                modelName = "model",
            ),
            vitsConfig = SpeechServicesPreferences.VitsTtsPackageConfig(
                packagePath = "/models/voice",
                speakerId = "speaker",
                options = mapOf("noise" to "0.1"),
            ),
            cleanerRegexs = listOf("<think>.*?</think>"),
            speechRate = 1.25f,
            pitch = 0.9f,
            createdAt = 10,
            updatedAt = 20,
        )
        val stt = SpeechServiceProfilesPreferences.SttProfile(
            id = "stt-1",
            name = "Primary STT",
            serviceType = SpeechServiceFactory.SpeechServiceType.DEEPGRAM_STT,
            httpConfig = SpeechServicesPreferences.SttHttpConfig(
                endpointUrl = "https://example.test/stt",
                apiKey = "stt-test-key",
                modelName = "model",
            ),
            createdAt = 30,
            updatedAt = 40,
        )

        val encodedTts = SpeechServiceProfilesPreferences.json.encodeToString(listOf(tts))
        val encodedStt = SpeechServiceProfilesPreferences.json.encodeToString(listOf(stt))

        assertEquals(listOf(tts), SpeechServiceProfilesPreferences.decodeTtsProfiles(encodedTts))
        assertEquals(listOf(stt), SpeechServiceProfilesPreferences.decodeSttProfiles(encodedStt))
    }

    @Test
    fun `ignores fields from a newer profile payload`() {
        val raw =
            """
            [{
              "id":"tts-1","name":"TTS","serviceType":"SIMPLE_TTS",
              "httpConfig":{"urlTemplate":"","apiKey":"","headers":{}},
              "vitsConfig":{"packagePath":"","speakerId":"","options":{}},
              "cleanerRegexs":[],"speechRate":1.0,"pitch":1.0,
              "createdAt":1,"updatedAt":2,"futureField":"ignored"
            }]
            """.trimIndent()

        assertEquals("tts-1", SpeechServiceProfilesPreferences.decodeTtsProfiles(raw).single().id)
    }

    @Test
    fun `rejects malformed profile payload instead of hiding corruption`() {
        assertThrows(Exception::class.java) {
            SpeechServiceProfilesPreferences.decodeSttProfiles("[{\"id\":\"missing-fields\"}]")
        }
    }

    @Test
    fun `blank profile payload represents an uninitialized store`() {
        assertEquals(emptyList<Any>(), SpeechServiceProfilesPreferences.decodeTtsProfiles(null))
        assertEquals(emptyList<Any>(), SpeechServiceProfilesPreferences.decodeSttProfiles(""))
    }
}
