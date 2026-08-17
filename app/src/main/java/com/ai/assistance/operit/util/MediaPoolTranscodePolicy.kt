package com.ai.assistance.operit.util

import kotlin.math.floor

internal enum class MediaPoolTranscodeKind {
    AUDIO,
    VIDEO,
}

internal data class MediaPoolTranscodePlan(
    val audioBitrateKbps: Int,
    val videoBitrateKbps: Int?,
)

internal fun resolveMediaPoolTranscodePlan(
    kind: MediaPoolTranscodeKind,
    durationSeconds: Double,
    targetBytes: Long,
): MediaPoolTranscodePlan? {
    if (!durationSeconds.isFinite() || durationSeconds <= 0.0 || targetBytes <= 0L) {
        return null
    }
    val totalBitrateKbps =
        floor(
            targetBytes.toDouble() *
                BITS_PER_BYTE *
                MEDIA_POOL_PAYLOAD_BUDGET_RATIO /
                durationSeconds /
                BITS_PER_KILOBIT,
        ).toLong()
    if (totalBitrateKbps <= 0L) {
        return null
    }

    return when (kind) {
        MediaPoolTranscodeKind.AUDIO -> {
            val audioBitrate =
                totalBitrateKbps
                    .coerceAtMost(MEDIA_POOL_AUDIO_MAX_BITRATE_KBPS.toLong())
                    .toInt()
            audioBitrate
                .takeIf { bitrate -> bitrate >= MEDIA_POOL_AUDIO_MIN_BITRATE_KBPS }
                ?.let { bitrate ->
                    MediaPoolTranscodePlan(
                        audioBitrateKbps = bitrate,
                        videoBitrateKbps = null,
                    )
                }
        }
        MediaPoolTranscodeKind.VIDEO -> {
            val videoBitrate =
                (totalBitrateKbps - MEDIA_POOL_VIDEO_AUDIO_BITRATE_KBPS)
                    .coerceAtMost(MEDIA_POOL_VIDEO_MAX_BITRATE_KBPS.toLong())
                    .toInt()
            videoBitrate
                .takeIf { bitrate -> bitrate >= MEDIA_POOL_VIDEO_MIN_BITRATE_KBPS }
                ?.let { bitrate ->
                    MediaPoolTranscodePlan(
                        audioBitrateKbps = MEDIA_POOL_VIDEO_AUDIO_BITRATE_KBPS,
                        videoBitrateKbps = bitrate,
                    )
                }
        }
    }
}

private const val BITS_PER_BYTE = 8.0
private const val BITS_PER_KILOBIT = 1_000.0

// 码率预算只使用目标容量的 85%，为 MP4/MP3 容器、编码器码率波动和尾部元数据留出固定空间。
// 这是一次编码的确定性预算，不是失败后的降级或第二次转码。
private const val MEDIA_POOL_PAYLOAD_BUDGET_RATIO = 0.85
private const val MEDIA_POOL_AUDIO_MIN_BITRATE_KBPS = 16
private const val MEDIA_POOL_AUDIO_MAX_BITRATE_KBPS = 96
private const val MEDIA_POOL_VIDEO_AUDIO_BITRATE_KBPS = 48
private const val MEDIA_POOL_VIDEO_MIN_BITRATE_KBPS = 96
private const val MEDIA_POOL_VIDEO_MAX_BITRATE_KBPS = 1_500
