package com.ai.assistance.operit.core.player

import android.content.Context
import androidx.core.content.edit
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

internal class PlayerSettingsStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readSettings())

    val state: StateFlow<PlayerSettings> = _state.asStateFlow()
    val current: PlayerSettings
        get() = _state.value

    fun setDecoderPreset(value: PlayerDecoderPreset) {
        preferences.edit { putString(KEY_DECODER_PRESET, value.persistedId) }
        _state.value = _state.value.copy(decoderPreset = value)
    }

    fun setGpuNextEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_GPU_NEXT_ENABLED, enabled) }
        _state.value = _state.value.copy(gpuNextEnabled = enabled)
    }

    fun setVulkanEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_VULKAN_ENABLED, enabled) }
        _state.value = _state.value.copy(vulkanEnabled = enabled)
    }

    fun setDefaultSpeed(value: Double) {
        require(value in PLAYER_SPEED_OPTIONS) { "Unsupported player speed: $value" }
        preferences.edit { putInt(KEY_DEFAULT_SPEED_PERCENT, (value * 100).toInt()) }
        _state.value = _state.value.copy(defaultSpeed = value)
    }

    fun setLastPlaybackSpeed(value: Double) {
        require(isSupportedPlayerSpeed(value)) { "Unsupported remembered player speed: $value" }
        preferences.edit { putInt(KEY_LAST_PLAYBACK_SPEED_PERCENT, (value * 100).roundToInt()) }
        _state.value = _state.value.copy(lastPlaybackSpeed = value)
    }

    fun setRememberPlaybackSpeed(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_REMEMBER_PLAYBACK_SPEED, enabled) }
        _state.value = _state.value.copy(rememberPlaybackSpeed = enabled)
    }

    fun setBackgroundBehavior(value: PlayerBackgroundBehavior) {
        preferences.edit { putString(KEY_BACKGROUND_BEHAVIOR, value.persistedId) }
        _state.value = _state.value.copy(backgroundBehavior = value)
    }

    fun setFullscreenExitBehavior(value: PlayerFullscreenExitBehavior) {
        preferences.edit { putString(KEY_FULLSCREEN_EXIT_BEHAVIOR, value.persistedId) }
        _state.value = _state.value.copy(fullscreenExitBehavior = value)
    }

    fun setFollowGravityRotation(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_FOLLOW_GRAVITY_ROTATION, enabled) }
        _state.value = _state.value.copy(followGravityRotation = enabled)
    }

    fun setAnime4KMode(value: Anime4KMode) {
        preferences.edit { putString(KEY_ANIME4K_MODE, value.persistedId) }
        _state.value = _state.value.copy(anime4KMode = value)
    }

    fun setRememberAnime4KMode(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_REMEMBER_ANIME4K_MODE, enabled) }
        _state.value = _state.value.copy(rememberAnime4KMode = enabled)
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_VOLUME_BOOST_ENABLED, enabled) }
        _state.value = _state.value.copy(volumeBoostEnabled = enabled)
    }

    fun setPreciseSeeking(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_PRECISE_SEEKING, enabled) }
        _state.value = _state.value.copy(preciseSeeking = enabled)
    }

    fun setSeekStepSeconds(value: Int) {
        require(value in PLAYER_SEEK_STEP_OPTIONS) { "Unsupported player seek step: $value" }
        preferences.edit { putInt(KEY_SEEK_STEP_SECONDS, value) }
        _state.value = _state.value.copy(seekStepSeconds = value)
    }

    fun setDoubleTapAction(value: PlayerDoubleTapAction) {
        preferences.edit { putString(KEY_DOUBLE_TAP_ACTION, value.persistedId) }
        _state.value = _state.value.copy(doubleTapAction = value)
    }

    fun setDoubleTapSeekSeconds(value: Int) {
        require(value in PLAYER_DOUBLE_TAP_SEEK_OPTIONS) {
            "Unsupported player double tap seek step: $value"
        }
        preferences.edit { putInt(KEY_DOUBLE_TAP_SEEK_SECONDS, value) }
        _state.value = _state.value.copy(doubleTapSeekSeconds = value)
    }

    fun setLongPressSpeedBoostEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_LONG_PRESS_SPEED_BOOST_ENABLED, enabled) }
        _state.value = _state.value.copy(longPressSpeedBoostEnabled = enabled)
    }

    fun setChapterBarEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_CHAPTER_BAR_ENABLED, enabled) }
        _state.value = _state.value.copy(chapterBarEnabled = enabled)
    }

    fun setSeekbarThumbnailEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SEEKBAR_THUMBNAIL_ENABLED, enabled) }
        _state.value = _state.value.copy(seekbarThumbnailEnabled = enabled)
    }

    fun setAutoPlayNext(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTO_PLAY_NEXT, enabled) }
        _state.value = _state.value.copy(autoPlayNext = enabled)
    }

    fun setQueueEndBehavior(value: PlayerQueueEndBehavior) {
        preferences.edit { putString(KEY_QUEUE_END_BEHAVIOR, value.persistedId) }
        _state.value = _state.value.copy(queueEndBehavior = value)
    }

    fun setNetworkCachePolicy(value: PlayerNetworkCachePolicy) {
        preferences.edit { putString(KEY_NETWORK_CACHE_POLICY, value.persistedId) }
        _state.value = _state.value.copy(networkCachePolicy = value)
    }

    fun setSubtitleScale(value: Double) {
        require(value in PLAYER_SUBTITLE_SCALE_OPTIONS) { "Unsupported subtitle scale: $value" }
        preferences.edit { putInt(KEY_SUBTITLE_SCALE_PERCENT, (value * 100).toInt()) }
        _state.value = _state.value.copy(subtitleScale = value)
    }

    fun setScreenshotDirectory(uri: String, displayName: String) {
        val normalizedUri = uri.trim()
        val normalizedName = displayName.trim()
        require(normalizedUri.isNotBlank()) { "Player screenshot directory URI is blank" }
        require(normalizedName.isNotBlank()) { "Player screenshot directory name is blank" }
        val previousUri = _state.value.screenshotDirectoryUri
        preferences.edit {
            putString(KEY_SCREENSHOT_DIRECTORY_URI, normalizedUri)
            putString(KEY_SCREENSHOT_DIRECTORY_NAME, normalizedName)
        }
        _state.value =
            _state.value.copy(
                screenshotDirectoryUri = normalizedUri,
                screenshotDirectoryName = normalizedName,
            )
        releaseReplacedDirectoryPermission(previousUri, normalizedUri)
    }

    fun clearScreenshotDirectory() {
        val previousUri = _state.value.screenshotDirectoryUri
        preferences.edit {
            remove(KEY_SCREENSHOT_DIRECTORY_URI)
            remove(KEY_SCREENSHOT_DIRECTORY_NAME)
        }
        _state.value =
            _state.value.copy(
                screenshotDirectoryUri = "",
                screenshotDirectoryName = "",
            )
        releaseReplacedDirectoryPermission(previousUri, "")
    }

    fun setVideoDownloadDirectory(uri: String, displayName: String) {
        val normalizedUri = uri.trim()
        val normalizedName = displayName.trim()
        require(normalizedUri.isNotBlank()) { "Player video download directory URI is blank" }
        require(normalizedName.isNotBlank()) { "Player video download directory name is blank" }
        val previousUri = _state.value.videoDownloadDirectoryUri
        preferences.edit {
            putString(KEY_VIDEO_DOWNLOAD_DIRECTORY_URI, normalizedUri)
            putString(KEY_VIDEO_DOWNLOAD_DIRECTORY_NAME, normalizedName)
        }
        _state.value =
            _state.value.copy(
                videoDownloadDirectoryUri = normalizedUri,
                videoDownloadDirectoryName = normalizedName,
            )
        releaseReplacedDirectoryPermission(previousUri, normalizedUri)
    }

    fun clearVideoDownloadDirectory() {
        val previousUri = _state.value.videoDownloadDirectoryUri
        preferences.edit {
            remove(KEY_VIDEO_DOWNLOAD_DIRECTORY_URI)
            remove(KEY_VIDEO_DOWNLOAD_DIRECTORY_NAME)
        }
        _state.value =
            _state.value.copy(
                videoDownloadDirectoryUri = "",
                videoDownloadDirectoryName = "",
            )
        releaseReplacedDirectoryPermission(previousUri, "")
    }

    private fun releaseReplacedDirectoryPermission(previousUri: String, currentUri: String) {
        if (previousUri.isNotBlank() && previousUri != currentUri) {
            BrowserDownloadManager.getInstance(appContext)
                .releasePersistedDirectoryPermissionIfUnused(previousUri)
        }
    }

    private fun readAnime4KMode(): Anime4KMode {
        val storedId =
            requireNotNull(
                preferences.getString(KEY_ANIME4K_MODE, Anime4KMode.OFF.persistedId),
            ) {
                "Anime4K preference is null"
            }
        val migratedId = migrateLegacyAnime4KPersistedId(storedId)
        if (migratedId != storedId) {
            // 七档方案替换了开发期四档 ID；必须先重写已存在的数据，否则严格枚举读取会在启动时崩溃。
            preferences.edit { putString(KEY_ANIME4K_MODE, migratedId) }
        }
        return Anime4KMode.fromPersistedId(migratedId)
    }

    private fun readSettings(): PlayerSettings {
        val speed = preferences.getInt(KEY_DEFAULT_SPEED_PERCENT, 100) / 100.0
        require(speed in PLAYER_SPEED_OPTIONS) { "Invalid persisted player speed: $speed" }
        val lastPlaybackSpeed =
            preferences.getInt(KEY_LAST_PLAYBACK_SPEED_PERCENT, 100) / 100.0
        require(isSupportedPlayerSpeed(lastPlaybackSpeed)) {
            "Invalid persisted remembered player speed: $lastPlaybackSpeed"
        }
        return PlayerSettings(
            decoderPreset =
                PlayerDecoderPreset.fromPersistedId(
                    requireNotNull(
                        preferences.getString(
                            KEY_DECODER_PRESET,
                            PlayerDecoderPreset.FAST.persistedId,
                        ),
                    ) { "Player decoder preset preference is null" },
                ),
            gpuNextEnabled = preferences.getBoolean(KEY_GPU_NEXT_ENABLED, false),
            vulkanEnabled = preferences.getBoolean(KEY_VULKAN_ENABLED, false),
            defaultSpeed = speed,
            lastPlaybackSpeed = lastPlaybackSpeed,
            rememberPlaybackSpeed = preferences.getBoolean(KEY_REMEMBER_PLAYBACK_SPEED, false),
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
            followGravityRotation = preferences.getBoolean(KEY_FOLLOW_GRAVITY_ROTATION, false),
            anime4KMode = readAnime4KMode(),
            rememberAnime4KMode = preferences.getBoolean(KEY_REMEMBER_ANIME4K_MODE, false),
            volumeBoostEnabled = preferences.getBoolean(KEY_VOLUME_BOOST_ENABLED, false),
            preciseSeeking = preferences.getBoolean(KEY_PRECISE_SEEKING, true),
            seekStepSeconds =
                preferences.getInt(KEY_SEEK_STEP_SECONDS, 10).also { value ->
                    require(value in PLAYER_SEEK_STEP_OPTIONS) {
                        "Invalid persisted player seek step: $value"
                    }
                },
            doubleTapAction =
                PlayerDoubleTapAction.fromPersistedId(
                    requireNotNull(
                        preferences.getString(
                            KEY_DOUBLE_TAP_ACTION,
                            PlayerDoubleTapAction.PLAY_PAUSE.persistedId,
                        ),
                    ) { "Player double tap action preference is null" },
                ),
            doubleTapSeekSeconds =
                preferences.getInt(KEY_DOUBLE_TAP_SEEK_SECONDS, 10).also { value ->
                    require(value in PLAYER_DOUBLE_TAP_SEEK_OPTIONS) {
                        "Invalid persisted player double tap seek step: $value"
                    }
                },
            longPressSpeedBoostEnabled =
                preferences.getBoolean(KEY_LONG_PRESS_SPEED_BOOST_ENABLED, false),
            chapterBarEnabled = preferences.getBoolean(KEY_CHAPTER_BAR_ENABLED, true),
            seekbarThumbnailEnabled =
                preferences.getBoolean(KEY_SEEKBAR_THUMBNAIL_ENABLED, true),
            autoPlayNext = preferences.getBoolean(KEY_AUTO_PLAY_NEXT, true),
            queueEndBehavior =
                PlayerQueueEndBehavior.fromPersistedId(
                    requireNotNull(
                        preferences.getString(
                            KEY_QUEUE_END_BEHAVIOR,
                            PlayerQueueEndBehavior.CLOSE.persistedId,
                        ),
                    ) { "Player queue end behavior preference is null" },
                ),
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
            screenshotDirectoryUri =
                requireNotNull(preferences.getString(KEY_SCREENSHOT_DIRECTORY_URI, "")) {
                    "Player screenshot directory URI preference is null"
                }.trim(),
            screenshotDirectoryName =
                requireNotNull(preferences.getString(KEY_SCREENSHOT_DIRECTORY_NAME, "")) {
                    "Player screenshot directory name preference is null"
                }.trim(),
            videoDownloadDirectoryUri =
                requireNotNull(preferences.getString(KEY_VIDEO_DOWNLOAD_DIRECTORY_URI, "")) {
                    "Player video download directory URI preference is null"
                }.trim(),
            videoDownloadDirectoryName =
                requireNotNull(preferences.getString(KEY_VIDEO_DOWNLOAD_DIRECTORY_NAME, "")) {
                    "Player video download directory name preference is null"
                }.trim(),
        ).also { settings ->
            require(
                settings.screenshotDirectoryUri.isBlank() ==
                    settings.screenshotDirectoryName.isBlank(),
            ) {
                "Player screenshot directory URI and name must be stored together"
            }
            require(
                settings.videoDownloadDirectoryUri.isBlank() ==
                    settings.videoDownloadDirectoryName.isBlank(),
            ) {
                "Player video download directory URI and name must be stored together"
            }
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "kiyori_player_settings"
        private const val KEY_DECODER_PRESET = "decoder_preset"
        private const val KEY_GPU_NEXT_ENABLED = "gpu_next_enabled"
        private const val KEY_VULKAN_ENABLED = "vulkan_enabled"
        private const val KEY_DEFAULT_SPEED_PERCENT = "default_speed_percent"
        private const val KEY_LAST_PLAYBACK_SPEED_PERCENT = "last_playback_speed_percent"
        private const val KEY_REMEMBER_PLAYBACK_SPEED = "remember_playback_speed"
        private const val KEY_BACKGROUND_BEHAVIOR = "background_behavior"
        private const val KEY_FULLSCREEN_EXIT_BEHAVIOR = "fullscreen_exit_behavior"
        private const val KEY_FOLLOW_GRAVITY_ROTATION = "follow_gravity_rotation"
        private const val KEY_ANIME4K_MODE = "anime4k_mode"
        private const val KEY_REMEMBER_ANIME4K_MODE = "remember_anime4k_mode"
        private const val KEY_VOLUME_BOOST_ENABLED = "volume_boost_enabled"
        private const val KEY_PRECISE_SEEKING = "precise_seeking"
        private const val KEY_SEEK_STEP_SECONDS = "seek_step_seconds"
        private const val KEY_DOUBLE_TAP_ACTION = "double_tap_action"
        private const val KEY_DOUBLE_TAP_SEEK_SECONDS = "double_tap_seek_seconds"
        private const val KEY_LONG_PRESS_SPEED_BOOST_ENABLED = "long_press_speed_boost_enabled"
        private const val KEY_CHAPTER_BAR_ENABLED = "chapter_bar_enabled"
        private const val KEY_SEEKBAR_THUMBNAIL_ENABLED = "seekbar_thumbnail_enabled"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_QUEUE_END_BEHAVIOR = "queue_end_behavior"
        private const val KEY_NETWORK_CACHE_POLICY = "network_cache_policy"
        private const val KEY_SUBTITLE_SCALE_PERCENT = "subtitle_scale_percent"
        private const val KEY_SCREENSHOT_DIRECTORY_URI = "screenshot_directory_uri"
        private const val KEY_SCREENSHOT_DIRECTORY_NAME = "screenshot_directory_name"
        private const val KEY_VIDEO_DOWNLOAD_DIRECTORY_URI = "video_download_directory_uri"
        private const val KEY_VIDEO_DOWNLOAD_DIRECTORY_NAME = "video_download_directory_name"

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
