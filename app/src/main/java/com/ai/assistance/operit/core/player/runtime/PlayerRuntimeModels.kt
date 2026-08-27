package com.ai.assistance.operit.core.player.runtime

import android.os.Parcelable
import com.ai.assistance.operit.core.player.PlayerDecoderBackend
import com.ai.assistance.operit.core.player.PlayerNetworkCachePolicy
import com.ai.assistance.operit.core.player.PlayerChapter
import com.ai.assistance.operit.core.player.PlayerRenderingProfile
import com.ai.assistance.operit.core.player.PlayerSettings
import com.ai.assistance.operit.core.player.PlayerTrack
import com.ai.assistance.operit.core.player.PlayerVideoFitMode
import com.ai.assistance.operit.core.player.isSupportedPlayerSpeed
import java.io.File
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class PlayerRuntimeConfig(
    val decoderBackendId: String,
    val renderingProfileId: String,
    val gpuNextEnabled: Boolean,
    val vulkanEnabled: Boolean,
    val preciseSeeking: Boolean,
    val networkCachePolicyId: String,
    val subtitleScale: Double,
    val volumeBoostEnabled: Boolean,
    val shaderFiles: List<String>,
) : Parcelable {
    init {
        PlayerDecoderBackend.fromPersistedId(decoderBackendId)
        PlayerRenderingProfile.fromPersistedId(renderingProfileId)
        PlayerNetworkCachePolicy.fromPersistedId(networkCachePolicyId)
        require(subtitleScale.isFinite() && subtitleScale > 0.0) {
            "Player runtime subtitle scale is invalid"
        }
        require(shaderFiles.all { path -> path.isNotBlank() && File(path).isAbsolute }) {
            "Player runtime shader path must be absolute"
        }
    }
}

internal enum class PlayerRuntimeMediaTransport(
    val persistedId: String,
) {
    DIRECT("direct"),
    MAIN_PROCESS_PROXY_BRIDGE("main_process_proxy_bridge"),
    LOCAL_DESCRIPTOR("local_descriptor"),
    ;

    companion object {
        fun fromPersistedId(value: String): PlayerRuntimeMediaTransport =
            entries.singleOrNull { it.persistedId == value }
                ?: error("Unknown player media transport: $value")
    }
}

@Parcelize
internal data class PlayerRuntimeLoadRequest(
    val requestId: String,
    val uri: String,
    val headers: Map<String, String>,
    val config: PlayerRuntimeConfig,
    val initialSpeed: Double,
    val transportId: String = PlayerRuntimeMediaTransport.DIRECT.persistedId,
) : Parcelable {
    init {
        require(requestId.isNotBlank()) { "Player runtime request ID is blank" }
        require(uri.isNotBlank()) { "Player runtime URI is blank" }
        PlayerRuntimeMediaTransport.fromPersistedId(transportId)
        require(isSupportedPlayerSpeed(initialSpeed)) {
            "Player runtime initial speed is invalid"
        }
        require(
            headers.all { (name, value) ->
                name.isNotBlank() &&
                    ':' !in name &&
                    '\r' !in name &&
                    '\n' !in name &&
                    '\r' !in value &&
                    '\n' !in value
            },
        ) {
            "Player runtime headers are invalid"
        }
    }
}

@Parcelize
internal data class PlayerRuntimePlaybackSnapshot(
    val positionSeconds: Double?,
    val durationSeconds: Double?,
    val paused: Boolean?,
    val buffering: Boolean?,
    val speed: Double?,
    val networkSpeedBytesPerSecond: Long,
    val fullVideoCacheActive: Boolean = false,
    val fullVideoCacheComplete: Boolean = false,
    val fullVideoCacheStartSeconds: Double? = null,
    val fullVideoCacheEndSeconds: Double? = null,
    val fullVideoCachePhase: String = PlayerFullVideoCachePhase.DISABLED.name,
    val fullVideoCacheReason: String? = null,
    val fullVideoCacheStateEvidence: String =
        PlayerFullVideoCacheStateEvidence.NOT_APPLICABLE.name,
    val fullVideoCacheFileBytes: Long = 0L,
    val fullVideoCacheExpectedBytes: Long? = null,
) : Parcelable

@Parcelize
internal data class PlayerRuntimeTrack(
    val id: Int,
    val title: String,
    val language: String?,
    val selected: Boolean,
) : Parcelable

@Parcelize
internal data class PlayerRuntimeTrackSnapshot(
    val audioTracks: List<PlayerRuntimeTrack>,
    val subtitleTracks: List<PlayerRuntimeTrack>,
    val chapters: List<PlayerRuntimeChapter>,
    val fileFormat: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val videoTrackCount: Int = 0,
    val activeHardwareDecoder: String? = null,
    val videoPixelFormat: String? = null,
    val videoCodecProfile: String? = null,
) : Parcelable

@Parcelize
internal data class PlayerRuntimeMediaIdentitySnapshot(
    val fileFormat: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val videoTrackCount: Int = 0,
    val activeHardwareDecoder: String? = null,
    val videoPixelFormat: String? = null,
    val videoCodecProfile: String? = null,
) : Parcelable

@Parcelize
internal data class PlayerRuntimeChapter(
    val title: String,
    val startSeconds: Double,
) : Parcelable {
    init {
        require(title.isNotBlank()) { "Player runtime chapter title is blank" }
        require(startSeconds.isFinite() && startSeconds >= 0.0) {
            "Player runtime chapter start time is invalid"
        }
    }
}

internal fun PlayerSettings.toRuntimeConfig(shaderFiles: List<String>): PlayerRuntimeConfig =
    PlayerRuntimeConfig(
        decoderBackendId = decoderBackend.persistedId,
        renderingProfileId = renderingProfile.persistedId,
        gpuNextEnabled = gpuNextEnabled,
        vulkanEnabled = vulkanEnabled,
        preciseSeeking = preciseSeeking,
        networkCachePolicyId = networkCachePolicy.persistedId,
        subtitleScale = subtitleScale,
        volumeBoostEnabled = volumeBoostEnabled,
        shaderFiles = shaderFiles.toList(),
    )

internal fun PlayerRuntimeConfig.toPlayerSettings(): PlayerSettings =
    PlayerSettings(
        decoderBackend = PlayerDecoderBackend.fromPersistedId(decoderBackendId),
        renderingProfile = PlayerRenderingProfile.fromPersistedId(renderingProfileId),
        gpuNextEnabled = gpuNextEnabled,
        vulkanEnabled = vulkanEnabled,
        preciseSeeking = preciseSeeking,
        networkCachePolicy = PlayerNetworkCachePolicy.fromPersistedId(networkCachePolicyId),
        subtitleScale = subtitleScale,
        volumeBoostEnabled = volumeBoostEnabled,
    )

internal fun PlayerTrack.toRuntimeTrack(): PlayerRuntimeTrack =
    PlayerRuntimeTrack(
        id = id,
        title = title,
        language = language,
        selected = selected,
    )

internal fun PlayerRuntimeTrack.toPlayerTrack(): PlayerTrack =
    PlayerTrack(
        id = id,
        title = title,
        language = language,
        selected = selected,
    )

internal fun PlayerChapter.toRuntimeChapter(): PlayerRuntimeChapter =
    PlayerRuntimeChapter(title = title, startSeconds = startSeconds)

internal fun PlayerRuntimeChapter.toPlayerChapter(): PlayerChapter =
    PlayerChapter(title = title, startSeconds = startSeconds)

internal fun PlayerVideoFitMode.toRuntimeId(): String = name

internal fun playerVideoFitModeFromRuntimeId(value: String): PlayerVideoFitMode =
    requireNotNull(PlayerVideoFitMode.entries.singleOrNull { mode -> mode.name == value }) {
        "Unsupported player video fit mode: $value"
    }
