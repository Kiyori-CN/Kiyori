package com.ai.assistance.operit.core.player.runtime

import android.os.Parcelable
import com.ai.assistance.operit.core.player.PlayerDecoderPreset
import com.ai.assistance.operit.core.player.PlayerEndBehavior
import com.ai.assistance.operit.core.player.PlayerNetworkCachePolicy
import com.ai.assistance.operit.core.player.PlayerSettings
import com.ai.assistance.operit.core.player.PlayerTrack
import com.ai.assistance.operit.core.player.PlayerVideoFitMode
import java.io.File
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class PlayerRuntimeConfig(
    val decoderPresetId: String,
    val gpuNextEnabled: Boolean,
    val vulkanEnabled: Boolean,
    val preciseSeeking: Boolean,
    val networkCachePolicyId: String,
    val subtitleScale: Double,
    val endBehaviorId: String,
    val volumeBoostEnabled: Boolean,
    val shaderFiles: List<String>,
) : Parcelable {
    init {
        PlayerDecoderPreset.fromPersistedId(decoderPresetId)
        PlayerNetworkCachePolicy.fromPersistedId(networkCachePolicyId)
        PlayerEndBehavior.fromPersistedId(endBehaviorId)
        require(subtitleScale.isFinite() && subtitleScale > 0.0) {
            "Player runtime subtitle scale is invalid"
        }
        require(shaderFiles.all { path -> path.isNotBlank() && File(path).isAbsolute }) {
            "Player runtime shader path must be absolute"
        }
    }
}

@Parcelize
internal data class PlayerRuntimeLoadRequest(
    val requestId: String,
    val uri: String,
    val headers: Map<String, String>,
    val config: PlayerRuntimeConfig,
    val initialSpeed: Double,
) : Parcelable {
    init {
        require(requestId.isNotBlank()) { "Player runtime request ID is blank" }
        require(uri.isNotBlank()) { "Player runtime URI is blank" }
        require(initialSpeed.isFinite() && initialSpeed > 0.0) {
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
) : Parcelable

internal fun PlayerSettings.toRuntimeConfig(shaderFiles: List<String>): PlayerRuntimeConfig =
    PlayerRuntimeConfig(
        decoderPresetId = decoderPreset.persistedId,
        gpuNextEnabled = gpuNextEnabled,
        vulkanEnabled = vulkanEnabled,
        preciseSeeking = preciseSeeking,
        networkCachePolicyId = networkCachePolicy.persistedId,
        subtitleScale = subtitleScale,
        endBehaviorId = endBehavior.persistedId,
        volumeBoostEnabled = volumeBoostEnabled,
        shaderFiles = shaderFiles.toList(),
    )

internal fun PlayerRuntimeConfig.toPlayerSettings(): PlayerSettings =
    PlayerSettings(
        decoderPreset = PlayerDecoderPreset.fromPersistedId(decoderPresetId),
        gpuNextEnabled = gpuNextEnabled,
        vulkanEnabled = vulkanEnabled,
        preciseSeeking = preciseSeeking,
        networkCachePolicy = PlayerNetworkCachePolicy.fromPersistedId(networkCachePolicyId),
        subtitleScale = subtitleScale,
        endBehavior = PlayerEndBehavior.fromPersistedId(endBehaviorId),
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

internal fun PlayerVideoFitMode.toRuntimeId(): String = name

internal fun playerVideoFitModeFromRuntimeId(value: String): PlayerVideoFitMode =
    requireNotNull(PlayerVideoFitMode.entries.singleOrNull { mode -> mode.name == value }) {
        "Unsupported player video fit mode: $value"
    }
