package com.ai.assistance.operit.core.player

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class PlayerPresentation {
    BROWSER_ONLY,
    FLOATING_PLAYER,
    FULLSCREEN_PLAYER,
}

internal enum class PlayerRuntimeState {
    STOPPED,
    BINDING,
    READY,
    ACTIVE,
    DEAD,
    CLOSING,
}

internal enum class PlayerMediaSource {
    EXTERNAL_INTENT,
    BROWSER_CANDIDATE,
    HISTORY_REPLAY,
}

internal enum class PlayerDecoderPreset(
    val persistedId: String,
    val displayName: String,
    val description: String,
) {
    FAST(
        "fast",
        "Fast",
        "硬解 + bilinear 缩放，整体功耗最低（推荐）",
    ),
    DEFAULT(
        "default",
        "Default",
        "默认配置，平衡画质与性能",
    ),
    HIGH_QUALITY(
        "high-quality",
        "High Quality",
        "高质量渲染，使用 ewa_lanczossharp 缩放",
    ),
    GPU_HQ(
        "gpu-hq",
        "GPU HQ",
        "GPU 高质量模式，开启去条带等后处理",
    ),
    LOW_LATENCY(
        "low-latency",
        "Low Latency",
        "低延迟模式，适合直播/在线流媒体",
    ),
    SW_FAST(
        "sw-fast",
        "SW Fast",
        "强制软解，GPU 负载最低但 CPU 功耗最高",
    ),
    ;

    companion object {
        fun fromPersistedId(value: String): PlayerDecoderPreset =
            requireNotNull(entries.singleOrNull { it.persistedId == value }) {
                "Unsupported player decoder preset: $value"
            }
    }
}

internal enum class PlayerBackgroundBehavior(val persistedId: String) {
    PAUSE("pause"),
    CONTINUE("continue"),
    ;

    companion object {
        fun fromPersistedId(value: String): PlayerBackgroundBehavior =
            requireNotNull(entries.singleOrNull { it.persistedId == value }) {
                "Unsupported player background behavior: $value"
            }
    }
}

internal enum class PlayerFullscreenExitBehavior(val persistedId: String) {
    RETURN_TO_FLOATING("return_to_floating"),
    CLOSE("close"),
    ;

    companion object {
        fun fromPersistedId(value: String): PlayerFullscreenExitBehavior =
            requireNotNull(entries.singleOrNull { it.persistedId == value }) {
                "Unsupported player fullscreen exit behavior: $value"
            }
    }
}

internal enum class PlayerNetworkCachePolicy(
    val persistedId: String,
    val forwardBytes: Long,
    val backwardBytes: Long,
    val cacheSeconds: Int,
) {
    COMPACT("compact", 64L * 1024L * 1024L, 32L * 1024L * 1024L, 60),
    BALANCED("balanced", 128L * 1024L * 1024L, 64L * 1024L * 1024L, 180),
    LARGE("large", 256L * 1024L * 1024L, 128L * 1024L * 1024L, 300),
    ;

    companion object {
        fun fromPersistedId(value: String): PlayerNetworkCachePolicy =
            requireNotNull(entries.singleOrNull { it.persistedId == value }) {
                "Unsupported player network cache policy: $value"
            }
    }
}

internal enum class PlayerDoubleTapAction(val persistedId: String) {
    PLAY_PAUSE("play_pause"),
    SEEK("seek"),
    ;

    companion object {
        fun fromPersistedId(value: String): PlayerDoubleTapAction =
            requireNotNull(entries.singleOrNull { it.persistedId == value }) {
                "Unsupported player double tap action: $value"
            }
    }
}

internal enum class PlayerQueueEndBehavior(val persistedId: String) {
    STAY("stay"),
    CLOSE("close"),
    LOOP_CURRENT("loop_current"),
    ;

    companion object {
        fun fromPersistedId(value: String): PlayerQueueEndBehavior =
            requireNotNull(entries.singleOrNull { it.persistedId == value }) {
                "Unsupported player queue end behavior: $value"
            }
    }
}

internal enum class PlayerVideoFitMode {
    FIT,
    STRETCH,
    CROP,
    ;

    fun next(): PlayerVideoFitMode = entries[(ordinal + 1) % entries.size]
}

internal enum class Anime4KMode(
    val persistedId: String,
    val shaderFiles: List<String>,
) {
    OFF("OFF", emptyList()),
    A(
        "A",
        listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        ),
    ),
    B(
        "B",
        listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_Soft_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        ),
    ),
    C(
        "C",
        listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Upscale_Denoise_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        ),
    ),
    A_PLUS(
        "A_PLUS",
        listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Restore_CNN_S.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        ),
    ),
    B_PLUS(
        "B_PLUS",
        listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_Soft_M.glsl",
            "Anime4K_Upscale_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Restore_CNN_Soft_S.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        ),
    ),
    C_PLUS(
        "C_PLUS",
        listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Upscale_Denoise_CNN_x2_M.glsl",
            "Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K_Restore_CNN_S.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        ),
    ),
    ;

    companion object {
        fun fromPersistedId(value: String): Anime4KMode =
            requireNotNull(entries.singleOrNull { it.persistedId == value }) {
                "Unsupported Anime4K mode: $value"
            }
    }
}

internal fun migrateLegacyAnime4KPersistedId(value: String): String =
    when (value) {
        "off" -> Anime4KMode.OFF.persistedId
        "fast" -> Anime4KMode.B.persistedId
        "balanced" -> Anime4KMode.A.persistedId
        "quality" -> Anime4KMode.A_PLUS.persistedId
        else -> value
    }

private const val MIN_PLAYER_SPEED_PERCENT = 1
private const val MAX_PLAYER_SPEED_PERCENT = 300
private const val PLAYER_SPEED_PRECISION_EPSILON = 0.000_000_1

internal val PLAYER_SPEED_OPTIONS = (25..MAX_PLAYER_SPEED_PERCENT step 25).map { it / 100.0 }
internal val PLAYER_SPEED_MENU_OPTIONS = PLAYER_SPEED_OPTIONS.asReversed()
internal val PLAYER_SEEK_STEP_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60)
internal val PLAYER_DOUBLE_TAP_SEEK_OPTIONS = listOf(5, 10, 15, 20, 30)
internal val PLAYER_SUBTITLE_SCALE_OPTIONS = listOf(0.8, 1.0, 1.2, 1.5)

internal fun isSupportedPlayerSpeed(speed: Double): Boolean {
    if (!speed.isFinite()) return false
    val speedPercent = (speed * 100.0).roundToInt()
    return speedPercent in MIN_PLAYER_SPEED_PERCENT..MAX_PLAYER_SPEED_PERCENT &&
        abs(speed - speedPercent / 100.0) <= PLAYER_SPEED_PRECISION_EPSILON
}

internal fun formatPlayerSpeedLabel(speed: Double): String {
    require(isSupportedPlayerSpeed(speed)) { "Unsupported player speed label: $speed" }
    val speedPercent = (speed * 100.0).roundToInt()
    return when {
        speedPercent % 100 == 0 -> "${speedPercent / 100}.0x"
        speedPercent % 10 == 0 -> "${speedPercent / 100}.${speedPercent % 100 / 10}x"
        else -> "${speedPercent / 100}.${(speedPercent % 100).toString().padStart(2, '0')}x"
    }
}

internal fun parsePlayerSpeedInput(input: String): Double? {
    val normalized = input.trim()
    if (!PLAYER_SPEED_INPUT_PATTERN.matches(normalized)) return null
    val speed = normalized.toDoubleOrNull() ?: return null
    return speed.takeIf { value -> value == 0.0 || isSupportedPlayerSpeed(value) }
}

internal fun resolveLongPressPlayerSpeed(currentSpeed: Double): Double? {
    require(isSupportedPlayerSpeed(currentSpeed)) {
        "Unsupported current player speed: $currentSpeed"
    }
    return when {
        currentSpeed < 1.0 -> 1.0
        currentSpeed < 2.0 -> 2.0
        currentSpeed < 3.0 -> 3.0
        else -> null
    }
}

private val PLAYER_SPEED_INPUT_PATTERN =
    Regex("""(?:0(?:\.\d{1,2})?|[12](?:\.\d{1,2})?|3(?:\.0{1,2})?)""")

internal data class LongPressSpeedBoostResult(
    val originalSpeed: Double,
    val boostedSpeed: Double,
)

@Immutable
internal data class PlayerSettings(
    val decoderPreset: PlayerDecoderPreset = PlayerDecoderPreset.FAST,
    val gpuNextEnabled: Boolean = false,
    val vulkanEnabled: Boolean = false,
    val defaultSpeed: Double = 1.0,
    val lastPlaybackSpeed: Double = 1.0,
    val rememberPlaybackSpeed: Boolean = false,
    val backgroundBehavior: PlayerBackgroundBehavior = PlayerBackgroundBehavior.PAUSE,
    val fullscreenExitBehavior: PlayerFullscreenExitBehavior =
        PlayerFullscreenExitBehavior.RETURN_TO_FLOATING,
    val followGravityRotation: Boolean = false,
    val anime4KMode: Anime4KMode = Anime4KMode.OFF,
    val rememberAnime4KMode: Boolean = false,
    val volumeBoostEnabled: Boolean = false,
    val preciseSeeking: Boolean = true,
    val seekStepSeconds: Int = 10,
    val doubleTapAction: PlayerDoubleTapAction = PlayerDoubleTapAction.PLAY_PAUSE,
    val doubleTapSeekSeconds: Int = 10,
    val longPressSpeedBoostEnabled: Boolean = false,
    val chapterBarEnabled: Boolean = true,
    val seekbarThumbnailEnabled: Boolean = true,
    val autoPlayNext: Boolean = true,
    val queueEndBehavior: PlayerQueueEndBehavior = PlayerQueueEndBehavior.CLOSE,
    val networkCachePolicy: PlayerNetworkCachePolicy = PlayerNetworkCachePolicy.BALANCED,
    val subtitleScale: Double = 1.0,
    val screenshotDirectoryUri: String = "",
    val screenshotDirectoryName: String = "",
    val videoDownloadDirectoryUri: String = "",
    val videoDownloadDirectoryName: String = "",
)

internal val FRESH_INSTALL_PLAYER_SETTINGS =
    PlayerSettings(
        decoderPreset = PlayerDecoderPreset.HIGH_QUALITY,
        rememberPlaybackSpeed = true,
        anime4KMode = Anime4KMode.A_PLUS,
        rememberAnime4KMode = true,
        longPressSpeedBoostEnabled = true,
        networkCachePolicy = PlayerNetworkCachePolicy.LARGE,
    )

@Immutable
internal data class PlayerMediaRequest(
    val requestId: String,
    val uri: String,
    val title: String,
    val headers: Map<String, String> = emptyMap(),
    val source: PlayerMediaSource,
    val sourceSessionId: String? = null,
    val cookieScopeUrl: String? = null,
    val sourcePageUrl: String? = null,
    val persistPlaybackHistory: Boolean = true,
) {
    init {
        require(requestId.isNotBlank()) { "Player request ID is blank" }
        require(uri.isNotBlank()) { "Player media URI is blank" }
        require(source != PlayerMediaSource.BROWSER_CANDIDATE || !sourceSessionId.isNullOrBlank()) {
            "Browser player request requires a source WebSession ID"
        }
    }
}

@Immutable
internal data class PlayerTrack(
    val id: Int,
    val title: String,
    val language: String?,
    val selected: Boolean,
)

@Immutable
internal data class PlayerChapter(
    val title: String,
    val startSeconds: Double,
) {
    init {
        require(title.isNotBlank()) { "Player chapter title is blank" }
        require(startSeconds.isFinite() && startSeconds >= 0.0) {
            "Player chapter start time is invalid"
        }
    }
}

@Immutable
internal data class PlayerSeekPreview(
    val positionSeconds: Double,
    val bitmap: Bitmap?,
    val loading: Boolean,
) {
    init {
        require(positionSeconds.isFinite() && positionSeconds >= 0.0) {
            "Player seek preview position is invalid"
        }
    }
}

@Immutable
internal data class PlayerSessionState(
    val request: PlayerMediaRequest? = null,
    val presentation: PlayerPresentation = PlayerPresentation.BROWSER_ONLY,
    val surfaceLease: PlayerSurfaceLeaseState = PlayerSurfaceLeaseState(),
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val paused: Boolean = true,
    val speed: Double = 1.0,
    val loading: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    val audioTracks: List<PlayerTrack> = emptyList(),
    val subtitleTracks: List<PlayerTrack> = emptyList(),
    val selectedAudioTrackId: Int? = null,
    val selectedSubtitleTrackId: Int? = null,
    val chapters: List<PlayerChapter> = emptyList(),
    val seekPreview: PlayerSeekPreview? = null,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val decoderPreset: PlayerDecoderPreset = PlayerDecoderPreset.FAST,
    val anime4KMode: Anime4KMode = Anime4KMode.OFF,
    val activeShaderFiles: List<String> = emptyList(),
    val networkSpeedBytesPerSecond: Long = 0L,
    val videoFitMode: PlayerVideoFitMode = PlayerVideoFitMode.FIT,
    val loadGeneration: Long = 0L,
    val runtimeGeneration: Long = 0L,
    val runtimeState: PlayerRuntimeState = PlayerRuntimeState.STOPPED,
    val runtimeProcessId: Int? = null,
) {
    init {
        require(queueSize >= 0) { "Player queue size cannot be negative" }
        require(
            (queueSize == 0 && queueIndex == 0) ||
                (queueSize > 0 && queueIndex in 0 until queueSize),
        ) {
            "Player queue index is invalid"
        }
    }

    val hasMedia: Boolean
        get() = request != null

    val hasPreviousQueueItem: Boolean
        get() = queueSize > 0 && queueIndex > 0

    val hasNextQueueItem: Boolean
        get() = queueSize > 0 && queueIndex < queueSize - 1

    val currentChapter: PlayerChapter?
        get() =
            chapters.lastOrNull { chapter ->
                chapter.startSeconds <= positionSeconds
            }
}

@Immutable
internal data class PlayerOpenTransition(
    val state: PlayerSessionState,
    val shouldLoad: Boolean,
)

internal fun isPlayerMediaLoadReady(hasPendingLoad: Boolean, hasAttachedSurface: Boolean): Boolean =
    hasPendingLoad && hasAttachedSurface

internal fun resolvePlayerOpenTransition(
    current: PlayerSessionState,
    request: PlayerMediaRequest,
    presentation: PlayerPresentation,
    settings: PlayerSettings,
): PlayerOpenTransition {
    require(presentation != PlayerPresentation.BROWSER_ONLY) {
        "A media request requires a player presentation"
    }
    if (current.request?.requestId == request.requestId) {
        return PlayerOpenTransition(
            state = current.copy(presentation = presentation),
            shouldLoad = false,
        )
    }
    return PlayerOpenTransition(
        state =
            PlayerSessionState(
                request = request,
                presentation = presentation,
                paused = false,
                speed = resolveInitialPlayerSpeed(settings),
                loading = true,
                decoderPreset = settings.decoderPreset,
                anime4KMode = resolveInitialAnime4KMode(settings),
                videoFitMode = PlayerVideoFitMode.FIT,
                surfaceLease = current.surfaceLease,
                queueIndex = current.queueIndex,
                queueSize = current.queueSize,
                loadGeneration = current.loadGeneration + 1L,
                runtimeGeneration = current.runtimeGeneration,
                runtimeState = current.runtimeState,
                runtimeProcessId = current.runtimeProcessId,
            ),
        shouldLoad = true,
    )
}

internal fun resolveInitialPlayerSpeed(settings: PlayerSettings): Double =
    if (settings.rememberPlaybackSpeed) settings.lastPlaybackSpeed else settings.defaultSpeed

internal fun resolveInitialAnime4KMode(settings: PlayerSettings): Anime4KMode =
    if (settings.rememberAnime4KMode) settings.anime4KMode else Anime4KMode.OFF

internal fun shouldReturnFullscreenPlayerToFloating(
    state: PlayerSessionState,
    settings: PlayerSettings,
): Boolean =
    state.request?.source == PlayerMediaSource.BROWSER_CANDIDATE &&
        settings.fullscreenExitBehavior == PlayerFullscreenExitBehavior.RETURN_TO_FLOATING

internal fun isPlayerAtNaturalEnd(
    positionSeconds: Double,
    durationSeconds: Double,
    toleranceSeconds: Double = 0.75,
): Boolean =
    durationSeconds.isFinite() &&
        positionSeconds.isFinite() &&
        durationSeconds > 0.0 &&
        toleranceSeconds >= 0.0 &&
        positionSeconds >= durationSeconds - toleranceSeconds
