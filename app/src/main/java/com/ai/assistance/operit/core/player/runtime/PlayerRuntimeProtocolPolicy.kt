package com.ai.assistance.operit.core.player.runtime

internal enum class PlayerRuntimeCommandType {
    INITIALIZE,
    LOAD,
    ATTACH_SURFACE,
    UPDATE_SURFACE,
    DETACH_SURFACE,
    SET_PAUSED,
    SEEK,
    SET_SPEED,
    SET_AUDIO_TRACK,
    SET_SUBTITLE_TRACK,
    APPLY_SETTINGS,
    APPLY_VIDEO_FIT,
    THUMBNAIL,
    SCREENSHOT,
    CLOSE,
}

internal data class PlayerRuntimeEventCursor(
    val runtimeGeneration: Long,
    val lastEventSequence: Long = 0L,
) {
    init {
        require(runtimeGeneration > 0L) { "Player runtime generation must be positive" }
        require(lastEventSequence >= 0L) { "Player runtime event sequence cannot be negative" }
    }
}

internal data class PlayerRuntimeEventAcceptance(
    val cursor: PlayerRuntimeEventCursor,
    val accepted: Boolean,
)

internal data class PlayerMpvHttpHeaderPlan(
    val forwardedHeaders: Map<String, String>,
    val rangeHeaderObserved: Boolean,
)

internal data class PlayerMpvEndFileState(
    val reason: String?,
    val fileError: String?,
    val failed: Boolean,
)

internal fun acceptPlayerRuntimeEvent(
    cursor: PlayerRuntimeEventCursor,
    runtimeGeneration: Long,
    eventSequence: Long,
): PlayerRuntimeEventAcceptance {
    val accepted =
        runtimeGeneration == cursor.runtimeGeneration &&
            eventSequence > cursor.lastEventSequence
    return PlayerRuntimeEventAcceptance(
        cursor =
            if (accepted) {
                cursor.copy(lastEventSequence = eventSequence)
            } else {
                cursor
            },
        accepted = accepted,
    )
}

internal fun buildPlayerMpvHttpHeaderPlan(
    headers: Map<String, String>,
): PlayerMpvHttpHeaderPlan {
    val forwardedHeaders = LinkedHashMap<String, String>()
    var rangeHeaderObserved = false
    headers.forEach { (name, value) ->
        require(
            name.isNotBlank() &&
                !name.contains(':') &&
                !name.contains('\n') &&
                !name.contains('\r'),
        ) {
            "Invalid player request header name"
        }
        require(!value.contains('\n') && !value.contains('\r')) {
            "Invalid player request header value"
        }
        if (name.equals("Range", ignoreCase = true)) {
            // Browser capture records one concrete request. Replaying that byte range as a
            // permanent custom header would override FFmpeg's current offset during open/seek.
            rangeHeaderObserved = true
        } else {
            forwardedHeaders[name] = value
        }
    }
    return PlayerMpvHttpHeaderPlan(
        forwardedHeaders = forwardedHeaders.toMap(),
        rangeHeaderObserved = rangeHeaderObserved,
    )
}

internal fun resolvePlayerMpvEndFileState(
    reason: String?,
    fileError: String?,
): PlayerMpvEndFileState =
    PlayerMpvEndFileState(
        reason = reason,
        fileError = fileError,
        failed = reason == "error",
    )
