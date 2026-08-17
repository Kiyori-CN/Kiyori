package com.ai.assistance.operit.core.player.runtime;

import android.graphics.Bitmap;
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeMediaIdentitySnapshot;
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimePlaybackSnapshot;
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeTrackSnapshot;

oneway interface IPlayerRuntimeCallback {
    void onRuntimeReady(
        long runtimeGeneration,
        long eventSequence,
        long commandId,
        int processId
    );
    void onCommandCompleted(long runtimeGeneration, long eventSequence, long commandId);
    void onCommandFailed(
        long runtimeGeneration,
        long eventSequence,
        long commandId,
        String operation,
        String message
    );
    void onSurfaceAttached(
        long runtimeGeneration,
        long eventSequence,
        long commandId,
        long surfaceGeneration
    );
    void onSurfaceDetached(
        long runtimeGeneration,
        long eventSequence,
        long commandId,
        long surfaceGeneration
    );
    void onPlaybackSnapshot(
        long runtimeGeneration,
        long eventSequence,
        in PlayerRuntimePlaybackSnapshot snapshot
    );
    void onFileLoaded(
        long runtimeGeneration,
        long eventSequence,
        long loadCommandId,
        in PlayerRuntimeTrackSnapshot tracks
    );
    void onMediaIdentityChanged(
        long runtimeGeneration,
        long eventSequence,
        long loadCommandId,
        in PlayerRuntimeMediaIdentitySnapshot identity
    );
    void onSeek(long runtimeGeneration, long eventSequence, long loadCommandId);
    void onPlaybackRestart(long runtimeGeneration, long eventSequence, long loadCommandId);
    void onNaturalEnd(long runtimeGeneration, long eventSequence);
    void onRuntimeError(long runtimeGeneration, long eventSequence, String message);
    void onDiagnosticLog(
        long runtimeGeneration,
        long eventSequence,
        int level,
        String tag,
        String message
    );
    void onThumbnailReady(
        long runtimeGeneration,
        long eventSequence,
        long commandId,
        double positionSeconds,
        in Bitmap bitmap
    );
    void onScreenshotCompleted(
        long runtimeGeneration,
        long eventSequence,
        long commandId,
        String path
    );
}
