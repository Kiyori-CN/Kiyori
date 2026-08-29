package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.speechServiceProfilesDataStore: DataStore<Preferences> by
    versionedPreferencesDataStore(
        name = "speech_service_profiles",
        currentVersion = 1,
    ) { appContext ->
        SpeechServiceProfilesPreferences.schemaMigration(appContext)
    }

/** Independent TTS/STT profiles with a one-time projection from legacy preferences. */
class SpeechServiceProfilesPreferences(private val context: Context) {
    @Serializable
    data class TtsProfile(
        val id: String,
        val name: String,
        val serviceType: VoiceServiceFactory.VoiceServiceType,
        val httpConfig: SpeechServicesPreferences.TtsHttpConfig,
        val vitsConfig: SpeechServicesPreferences.VitsTtsPackageConfig,
        val cleanerRegexs: List<String>,
        val speechRate: Float,
        val pitch: Float,
        val createdAt: Long,
        val updatedAt: Long,
    )

    @Serializable
    data class SttProfile(
        val id: String,
        val name: String,
        val serviceType: SpeechServiceFactory.SpeechServiceType,
        val httpConfig: SpeechServicesPreferences.SttHttpConfig,
        val createdAt: Long,
        val updatedAt: Long,
    )

    companion object {
        private const val LEGACY_TTS_PROFILE_ID = "legacy-tts-profile"
        private const val LEGACY_STT_PROFILE_ID = "legacy-stt-profile"
        private val TTS_PROFILES = stringPreferencesKey("tts_profiles")
        private val STT_PROFILES = stringPreferencesKey("stt_profiles")
        private val CURRENT_TTS_PROFILE_ID = stringPreferencesKey("current_tts_profile_id")
        private val CURRENT_STT_PROFILE_ID = stringPreferencesKey("current_stt_profile_id")
        internal val json = Json { ignoreUnknownKeys = true }

        internal fun schemaMigration(context: Context): PreferencesSchemaMigration =
            preferenceSchemaMigration { version, preferences ->
                require(version == 0) { "Missing speech profile migration from version $version" }
                migrateVersionZero(context.applicationContext, preferences)
            }

        private suspend fun migrateVersionZero(context: Context, preferences: MutablePreferences) {
            val legacy = SpeechServicesPreferences(context)
            val now = System.currentTimeMillis()
            val tts = TtsProfile(
                id = LEGACY_TTS_PROFILE_ID,
                name = context.getString(R.string.speech_services_profile_migrated_tts),
                serviceType = legacy.ttsServiceTypeFlow.first(),
                httpConfig = legacy.ttsHttpConfigFlow.first(),
                vitsConfig = legacy.ttsVitsPackageConfigFlow.first(),
                cleanerRegexs = legacy.ttsCleanerRegexsFlow.first(),
                speechRate = legacy.ttsSpeechRateFlow.first(),
                pitch = legacy.ttsPitchFlow.first(),
                createdAt = now,
                updatedAt = now,
            )
            val stt = SttProfile(
                id = LEGACY_STT_PROFILE_ID,
                name = context.getString(R.string.speech_services_profile_migrated_stt),
                serviceType = legacy.sttServiceTypeFlow.first(),
                httpConfig = legacy.sttHttpConfigFlow.first(),
                createdAt = now,
                updatedAt = now,
            )
            val ttsProfiles = decodeTtsProfiles(preferences[TTS_PROFILES]).ifEmpty { listOf(tts) }
            val sttProfiles = decodeSttProfiles(preferences[STT_PROFILES]).ifEmpty { listOf(stt) }
            preferences[TTS_PROFILES] = json.encodeToString(ttsProfiles)
            preferences[STT_PROFILES] = json.encodeToString(sttProfiles)
            preferences[CURRENT_TTS_PROFILE_ID] = preferences[CURRENT_TTS_PROFILE_ID]
                ?.takeIf { id -> ttsProfiles.any { it.id == id } } ?: ttsProfiles.first().id
            preferences[CURRENT_STT_PROFILE_ID] = preferences[CURRENT_STT_PROFILE_ID]
                ?.takeIf { id -> sttProfiles.any { it.id == id } } ?: sttProfiles.first().id
        }

        internal fun decodeTtsProfiles(raw: String?): List<TtsProfile> =
            if (raw.isNullOrBlank()) emptyList() else json.decodeFromString(ListSerializer(TtsProfile.serializer()), raw)

        internal fun decodeSttProfiles(raw: String?): List<SttProfile> =
            if (raw.isNullOrBlank()) emptyList() else json.decodeFromString(ListSerializer(SttProfile.serializer()), raw)
    }

    private val dataStore = context.applicationContext.speechServiceProfilesDataStore
    private val legacyPreferences = SpeechServicesPreferences(context.applicationContext)

    val ttsProfilesFlow: Flow<List<TtsProfile>> = dataStore.data.map { decodeTtsProfiles(it[TTS_PROFILES]) }
    val sttProfilesFlow: Flow<List<SttProfile>> = dataStore.data.map { decodeSttProfiles(it[STT_PROFILES]) }
    val currentTtsProfileIdFlow: Flow<String> = dataStore.data.map { it[CURRENT_TTS_PROFILE_ID].orEmpty() }
    val currentSttProfileIdFlow: Flow<String> = dataStore.data.map { it[CURRENT_STT_PROFILE_ID].orEmpty() }
    val currentTtsProfileOrNullFlow: Flow<TtsProfile?> =
        dataStore.data.map { preferences ->
            val currentId = preferences[CURRENT_TTS_PROFILE_ID].orEmpty()
            decodeTtsProfiles(preferences[TTS_PROFILES]).find { it.id == currentId }
        }
    val currentSttProfileOrNullFlow: Flow<SttProfile?> =
        dataStore.data.map { preferences ->
            val currentId = preferences[CURRENT_STT_PROFILE_ID].orEmpty()
            decodeSttProfiles(preferences[STT_PROFILES]).find { it.id == currentId }
        }

    suspend fun getCurrentTtsProfile(): TtsProfile = currentTtsProfileOrNullFlow.first()
        ?: error("Current TTS profile is missing")
    suspend fun getCurrentSttProfile(): SttProfile = currentSttProfileOrNullFlow.first()
        ?: error("Current STT profile is missing")

    suspend fun createTtsProfile(name: String, template: TtsProfile? = null): TtsProfile {
        val base = template ?: getCurrentTtsProfile()
        val now = System.currentTimeMillis()
        val profile = base.copy(id = UUID.randomUUID().toString(), name = requireName(name), createdAt = now, updatedAt = now)
        dataStore.edit { preferences ->
            preferences[TTS_PROFILES] = json.encodeToString(decodeTtsProfiles(preferences[TTS_PROFILES]) + profile)
            preferences[CURRENT_TTS_PROFILE_ID] = profile.id
        }
        projectTtsProfile(profile)
        return profile
    }

    suspend fun updateTtsProfile(profile: TtsProfile): TtsProfile {
        val normalized = profile.copy(
            name = requireName(profile.name),
            cleanerRegexs = profile.cleanerRegexs.filter(String::isNotBlank),
            speechRate = requirePositive(profile.speechRate, "TTS speech rate"),
            pitch = requirePositive(profile.pitch, "TTS pitch"),
            updatedAt = System.currentTimeMillis(),
        )
        var active = false
        dataStore.edit { preferences ->
            val existing = decodeTtsProfiles(preferences[TTS_PROFILES]).find { it.id == normalized.id }
                ?: error("TTS profile does not exist: ${normalized.id}")
            preferences[TTS_PROFILES] = json.encodeToString(
                decodeTtsProfiles(preferences[TTS_PROFILES]).map { if (it.id == normalized.id) normalized.copy(createdAt = existing.createdAt) else it }
            )
            active = preferences[CURRENT_TTS_PROFILE_ID] == normalized.id
        }
        val updated = ttsProfilesFlow.first().first { it.id == normalized.id }
        if (active) projectTtsProfile(updated)
        return updated
    }

    suspend fun createSttProfile(name: String, template: SttProfile? = null): SttProfile {
        val base = template ?: getCurrentSttProfile()
        val now = System.currentTimeMillis()
        val profile = base.copy(id = UUID.randomUUID().toString(), name = requireName(name), createdAt = now, updatedAt = now)
        dataStore.edit { preferences ->
            preferences[STT_PROFILES] = json.encodeToString(decodeSttProfiles(preferences[STT_PROFILES]) + profile)
            preferences[CURRENT_STT_PROFILE_ID] = profile.id
        }
        projectSttProfile(profile)
        return profile
    }

    suspend fun updateSttProfile(profile: SttProfile): SttProfile {
        val normalized = profile.copy(name = requireName(profile.name), updatedAt = System.currentTimeMillis())
        var active = false
        dataStore.edit { preferences ->
            val existing = decodeSttProfiles(preferences[STT_PROFILES]).find { it.id == normalized.id }
                ?: error("STT profile does not exist: ${normalized.id}")
            preferences[STT_PROFILES] = json.encodeToString(
                decodeSttProfiles(preferences[STT_PROFILES]).map { if (it.id == normalized.id) normalized.copy(createdAt = existing.createdAt) else it }
            )
            active = preferences[CURRENT_STT_PROFILE_ID] == normalized.id
        }
        val updated = sttProfilesFlow.first().first { it.id == normalized.id }
        if (active) projectSttProfile(updated)
        return updated
    }

    suspend fun selectTtsProfile(id: String) {
        val profile = ttsProfilesFlow.first().first { it.id == id }
        dataStore.edit { it[CURRENT_TTS_PROFILE_ID] = id }
        projectTtsProfile(profile)
    }

    suspend fun selectSttProfile(id: String) {
        val profile = sttProfilesFlow.first().first { it.id == id }
        dataStore.edit { it[CURRENT_STT_PROFILE_ID] = id }
        projectSttProfile(profile)
    }

    suspend fun renameTtsProfile(id: String, name: String): TtsProfile =
        updateTtsProfile(getTtsProfile(id).copy(name = name))

    suspend fun renameSttProfile(id: String, name: String): SttProfile =
        updateSttProfile(getSttProfile(id).copy(name = name))

    /** Synchronizes the legacy settings editor into the active profiles. */
    suspend fun syncFromLegacyPreferences() {
        val tts = getCurrentTtsProfile()
        val stt = getCurrentSttProfile()
        updateTtsProfile(
            tts.copy(
                serviceType = legacyPreferences.ttsServiceTypeFlow.first(),
                httpConfig = legacyPreferences.ttsHttpConfigFlow.first(),
                vitsConfig = legacyPreferences.ttsVitsPackageConfigFlow.first(),
                cleanerRegexs = legacyPreferences.ttsCleanerRegexsFlow.first(),
                speechRate = legacyPreferences.ttsSpeechRateFlow.first(),
                pitch = legacyPreferences.ttsPitchFlow.first(),
            )
        )
        updateSttProfile(
            stt.copy(
                serviceType = legacyPreferences.sttServiceTypeFlow.first(),
                httpConfig = legacyPreferences.sttHttpConfigFlow.first(),
            )
        )
    }

    suspend fun deleteTtsProfile(id: String) {
        dataStore.edit { preferences ->
            check(preferences[CURRENT_TTS_PROFILE_ID] != id) { "The active TTS profile cannot be deleted" }
            val profiles = decodeTtsProfiles(preferences[TTS_PROFILES])
            check(profiles.any { it.id == id }) { "TTS profile does not exist: $id" }
            preferences[TTS_PROFILES] = json.encodeToString(profiles.filterNot { it.id == id })
        }
    }

    suspend fun deleteSttProfile(id: String) {
        dataStore.edit { preferences ->
            check(preferences[CURRENT_STT_PROFILE_ID] != id) { "The active STT profile cannot be deleted" }
            val profiles = decodeSttProfiles(preferences[STT_PROFILES])
            check(profiles.any { it.id == id }) { "STT profile does not exist: $id" }
            preferences[STT_PROFILES] = json.encodeToString(profiles.filterNot { it.id == id })
        }
    }

    private suspend fun getTtsProfile(id: String): TtsProfile =
        ttsProfilesFlow.first().first { it.id == id }

    private suspend fun getSttProfile(id: String): SttProfile =
        sttProfilesFlow.first().first { it.id == id }

    private suspend fun projectTtsProfile(profile: TtsProfile) {
        legacyPreferences.saveTtsSettings(profile.serviceType, profile.httpConfig, profile.vitsConfig, profile.cleanerRegexs, profile.speechRate, profile.pitch)
    }

    private suspend fun projectSttProfile(profile: SttProfile) {
        legacyPreferences.saveSttSettings(profile.serviceType, profile.httpConfig)
    }

    private fun requireName(name: String): String = name.trim().also { check(it.isNotEmpty()) { "Speech profile name is empty" } }
    private fun requirePositive(value: Float, label: String): Float = value.also { check(it > 0f) { "$label must be positive" } }
}
