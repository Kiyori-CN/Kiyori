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
