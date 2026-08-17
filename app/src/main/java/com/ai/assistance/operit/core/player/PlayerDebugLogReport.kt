package com.ai.assistance.operit.core.player

import android.content.Context
import android.os.Build
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.core.player.runtime.capturePlayerNetworkSnapshot
import java.time.Instant

internal fun buildPlayerDebugLogReport(
    context: Context,
    state: PlayerSessionState,
    filter: PlayerDebugLogFilter = PlayerDebugLogFilter.ALL,
): String {
    val snapshot = PlayerDebugLogBuffer.snapshotState(filter)
    val request = state.request
    val surface = state.surfaceLease
    val mainProcessNetwork = capturePlayerNetworkSnapshot(context)
    return buildString {
        appendLine("Kiyori Player Diagnostic Report")
        appendLine("Generated: ${Instant.now()}")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}; " +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
        appendLine("Package: ${context.packageName}")
        appendLine()
        appendLine("Session")
        appendLine("presentation=${state.presentation}")
        appendLine(
            "runtime=${state.runtimeState} generation=${state.runtimeGeneration} " +
                "pid=${state.runtimeProcessId ?: "none"}",
        )
        appendLine(
            "loading=${state.loading} buffering=${state.buffering} seeking=${state.seeking} " +
                "paused=${state.paused} " +
                "position=${state.positionSeconds} duration=${state.durationSeconds} speed=${state.speed}",
        )
        appendLine(
            "decoderBackend=${state.decoderBackend.persistedId} " +
                "renderingProfile=${state.renderingProfile.persistedId} " +
                "anime4k=${state.anime4KMode.persistedId} " +
                "videoFit=${state.videoFitMode} networkBytesPerSecond=${state.networkSpeedBytesPerSecond}",
        )
        appendLine(
            "tracks=audio:${state.audioTracks.size}/selected:${state.selectedAudioTrackId ?: "none"} " +
                "subtitle:${state.subtitleTracks.size}/selected:${state.selectedSubtitleTrackId ?: "none"} " +
                "video:${state.videoTrackCount}",
        )
        appendLine(
            "mediaIdentity=container:${state.mediaContainer ?: "unknown"} " +
                "videoCodec:${state.videoCodec ?: "none"} audioCodec:${state.audioCodec ?: "none"} " +
                "hwdec:${state.activeHardwareDecoder ?: "none"} " +
                "pixelFormat:${state.videoPixelFormat ?: "unknown"} " +
                "codecProfile:${state.videoCodecProfile ?: "unknown"}",
        )
        appendLine(
            "fullVideoCache=active:${state.fullVideoCacheActive} " +
                "complete:${state.fullVideoCacheComplete} " +
                "phase:${state.fullVideoCachePhase} " +
                "reason:${state.fullVideoCacheReason ?: "none"} " +
                "stateEvidence:${state.fullVideoCacheStateEvidence} " +
                "range:${state.fullVideoCacheStartSeconds ?: "none"}-" +
                "${state.fullVideoCacheEndSeconds ?: "none"} " +
                "fileBytes:${state.fullVideoCacheFileBytes} " +
                "expectedBytes:${state.fullVideoCacheExpectedBytes ?: "unknown"}",
        )
        appendLine(
            "queue=index:${state.queueIndex}/size:${state.queueSize} " +
                "previous:${state.hasPreviousQueueItem} next:${state.hasNextQueueItem}",
        )
        appendLine(
            "chapters=${state.chapters.size} " +
                "current=${state.currentChapter?.let { "present" } ?: "none"} " +
                "seekPreview=${state.seekPreview?.let { if (it.loading) "loading" else "ready" } ?: "none"}",
        )
        appendLine(
            "surface=phase:${surface.phase} native:${surface.nativeState} generation:${surface.generation} " +
                "owner:${surface.currentOwner?.role ?: "none"} pending:${surface.pendingTarget?.role ?: "none"}",
        )
        state.error?.let { error ->
            appendLine("visibleError=${sanitizePlayerDiagnosticMessage(error)}")
        }
        appendLine()
        appendLine("Media")
        if (request == null) {
            appendLine("request=none")
        } else {
            appendLine(
                "request=${shortPlayerDiagnosticId(request.requestId)} source=${request.source} " +
                    "media=${describePlayerMediaUriForDiagnostics(request.uri)}",
            )
            appendLine("requestHeaderValues=omitted count=${request.headers.size}")
        }
        appendLine()
        appendLine("Network")
        appendLine("mainProcess=${mainProcessNetwork.diagnosticSummary()}")
        appendLine("playerProcess=see ordered PlayerNetwork log entries")
        appendLine()
        appendLine("Privacy")
        appendLine(
            "Request header values, Cookie, Authorization, URL query values, IP/DNS/proxy addresses, " +
                "network handles, titles and private paths are omitted.",
        )
        appendLine()
        appendLine("Log filter=${filter.displayName}")
        appendLine(
            "Logs matching=${snapshot.lineCount} total=${snapshot.totalLineCount} " +
                "dropped=${snapshot.droppedLineCount}",
        )
        appendLine("========================================")
        if (snapshot.text.isBlank()) {
            appendLine("No player log entries for the selected filter.")
        } else {
            append(snapshot.text)
        }
    }.trimEnd()
}
