package com.ai.assistance.operit.core.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PlayerSettingsStore private constructor(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readSettings())

    val state: StateFlow<PlayerSettings> = _state.asStateFlow()
    val current: PlayerSettings
        get() = _state.value

    fun setHardwareDecodingPolicy(value: PlayerHardwareDecodingPolicy) {
        preferences.edit().putString(KEY_HARDWARE_DECODING, value.persistedId).apply()
        _state.value = _state.value.copy(hardwareDecodingPolicy = value)
    }

    fun setDefaultSpeed(value: Double) {
        require(value in PLAYER_SPEED_OPTIONS) { "Unsupported player speed: $value" }
        preferences.edit().putInt(KEY_DEFAULT_SPEED_PERCENT, (value * 100).toInt()).apply()
        _state.value = _state.value.copy(defaultSpeed = value)
    }

    fun setBackgroundBehavior(value: PlayerBackgroundBehavior) {
        preferences.edit().putString(KEY_BACKGROUND_BEHAVIOR, value.persistedId).apply()
        _state.value = _state.value.copy(backgroundBehavior = value)
    }

    fun setFullscreenExitBehavior(value: PlayerFullscreenExitBehavior) {
        preferences.edit().putString(KEY_FULLSCREEN_EXIT_BEHAVIOR, value.persistedId).apply()
        _state.value = _state.value.copy(fullscreenExitBehavior = value)
    }

    fun setAnime4KMode(value: Anime4KMode) {
        preferences.edit().putString(KEY_ANIME4K_MODE, value.persistedId).apply()
        _state.value = _state.value.copy(anime4KMode = value)
    }

    fun setPreciseSeeking(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PRECISE_SEEKING, enabled).apply()
        _state.value = _state.value.copy(preciseSeeking = enabled)
    }

    fun setSeekStepSeconds(value: Int) {
        require(value in PLAYER_SEEK_STEP_OPTIONS) { "Unsupported player seek step: $value" }
        preferences.edit().putInt(KEY_SEEK_STEP_SECONDS, value).apply()
        _state.value = _state.value.copy(seekStepSeconds = value)
    }

    fun setNetworkCachePolicy(value: PlayerNetworkCachePolicy) {
        preferences.edit().putString(KEY_NETWORK_CACHE_POLICY, value.persistedId).apply()
        _state.value = _state.value.copy(networkCachePolicy = value)
    }

    fun setSubtitleScale(value: Double) {
        require(value in PLAYER_SUBTITLE_SCALE_OPTIONS) { "Unsupported subtitle scale: $value" }
        preferences.edit().putInt(KEY_SUBTITLE_SCALE_PERCENT, (value * 100).toInt()).apply()
        _state.value = _state.value.copy(subtitleScale = value)
    }

    fun setEndBehavior(value: PlayerEndBehavior) {
        preferences.edit().putString(KEY_END_BEHAVIOR, value.persistedId).apply()
        _state.value = _state.value.copy(endBehavior = value)
    }

    private fun readSettings(): PlayerSettings {
        val speed = preferences.getInt(KEY_DEFAULT_SPEED_PERCENT, 100) / 100.0
        require(speed in PLAYER_SPEED_OPTIONS) { "Invalid persisted player speed: $speed" }
        return PlayerSettings(
            hardwareDecodingPolicy =
                PlayerHardwareDecodingPolicy.fromPersistedId(
                    requireNotNull(
                        preferences.getString(
                            KEY_HARDWARE_DECODING,
                            PlayerHardwareDecodingPolicy.AUTOMATIC.persistedId,
                        ),
                    ) { "Player hardware decoding preference is null" },
                ),
            defaultSpeed = speed,
            backgroundBehavior =
                PlayerBackgroundBehavior.fromPersistedId(
                    requireNotNull(
                        preferences.getString(
                            KEY_BACKGROUND_BEHAVIOR,
                            PlayerBackgroundBehavior.PAUSE.persistedId,
                        ),
                    ) { "Player background behavior preference is null" },
                ),
            fullscreenExitBehavior =
                PlayerFullscreenExitBehavior.fromPersistedId(
                    requireNotNull(
                        preferences.getString(
                            KEY_FULLSCREEN_EXIT_BEHAVIOR,
                            PlayerFullscreenExitBehavior.RETURN_TO_FLOATING.persistedId,
                        ),
                    ) { "Player fullscreen exit preference is null" },
                ),
            anime4KMode =
                Anime4KMode.fromPersistedId(
                    requireNotNull(
                        preferences.getString(KEY_ANIME4K_MODE, Anime4KMode.OFF.persistedId),
                    ) { "Anime4K preference is null" },
                ),
            preciseSeeking = preferences.getBoolean(KEY_PRECISE_SEEKING, true),
            seekStepSeconds =
                preferences.getInt(KEY_SEEK_STEP_SECONDS, 10).also { value ->
                    require(value in PLAYER_SEEK_STEP_OPTIONS) {
                        "Invalid persisted player seek step: $value"
                    }
                },
            networkCachePolicy =
                PlayerNetworkCachePolicy.fromPersistedId(
                    requireNotNull(
                        preferences.getString(
                            KEY_NETWORK_CACHE_POLICY,
                            PlayerNetworkCachePolicy.BALANCED.persistedId,
                        ),
                    ) { "Player network cache preference is null" },
                ),
            subtitleScale =
                (preferences.getInt(KEY_SUBTITLE_SCALE_PERCENT, 100) / 100.0).also { value ->
                    require(value in PLAYER_SUBTITLE_SCALE_OPTIONS) {
                        "Invalid persisted subtitle scale: $value"
                    }
                },
            endBehavior =
                PlayerEndBehavior.fromPersistedId(
                    requireNotNull(
                        preferences.getString(KEY_END_BEHAVIOR, PlayerEndBehavior.PAUSE.persistedId),
                    ) { "Player end behavior preference is null" },
                ),
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "kiyori_player_settings"
        private const val KEY_HARDWARE_DECODING = "hardware_decoding"
        private const val KEY_DEFAULT_SPEED_PERCENT = "default_speed_percent"
        private const val KEY_BACKGROUND_BEHAVIOR = "background_behavior"
        private const val KEY_FULLSCREEN_EXIT_BEHAVIOR = "fullscreen_exit_behavior"
        private const val KEY_ANIME4K_MODE = "anime4k_mode"
        private const val KEY_PRECISE_SEEKING = "precise_seeking"
        private const val KEY_SEEK_STEP_SECONDS = "seek_step_seconds"
        private const val KEY_NETWORK_CACHE_POLICY = "network_cache_policy"
        private const val KEY_SUBTITLE_SCALE_PERCENT = "subtitle_scale_percent"
        private const val KEY_END_BEHAVIOR = "end_behavior"

        @Volatile private var instance: PlayerSettingsStore? = null

        fun getInstance(context: Context): PlayerSettingsStore =
            instance ?: synchronized(this) {
                instance
                    ?: PlayerSettingsStore(context.applicationContext).also { store ->
                        instance = store
                    }
            }
    }
}
