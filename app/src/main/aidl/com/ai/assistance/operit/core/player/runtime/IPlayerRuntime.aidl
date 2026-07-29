package com.ai.assistance.operit.core.player.runtime;

import android.view.Surface;
import com.ai.assistance.operit.core.player.runtime.IPlayerRuntimeCallback;
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeConfig;
import com.ai.assistance.operit.core.player.runtime.PlayerRuntimeLoadRequest;

oneway interface IPlayerRuntime {
    void registerCallback(long runtimeGeneration, IPlayerRuntimeCallback callback);
    void initialize(long runtimeGeneration, long commandId, in PlayerRuntimeConfig config);
    void load(long runtimeGeneration, long commandId, in PlayerRuntimeLoadRequest request);
    void attachSurface(
        long runtimeGeneration,
        long commandId,
        long surfaceGeneration,
        in Surface surface,
        int width,
        int height
    );
    void updateSurface(
        long runtimeGeneration,
        long commandId,
        long surfaceGeneration,
        int width,
        int height
    );
    void detachSurface(long runtimeGeneration, long commandId, long surfaceGeneration);
    void setPaused(long runtimeGeneration, long commandId, boolean paused);
    void seekTo(long runtimeGeneration, long commandId, double positionSeconds, boolean precise);
    void setSpeed(long runtimeGeneration, long commandId, double speed);
    void setAudioTrack(long runtimeGeneration, long commandId, int trackId);
    void setSubtitleTrack(
        long runtimeGeneration,
        long commandId,
        int trackId,
        boolean disabled
    );
    void applySettings(long runtimeGeneration, long commandId, in PlayerRuntimeConfig config);
    void applyVideoFitMode(long runtimeGeneration, long commandId, String mode);
    void requestThumbnail(
        long runtimeGeneration,
        long commandId,
        long loadCommandId,
        double positionSeconds,
        int maxSize
    );
    void captureScreenshot(long runtimeGeneration, long commandId, String path);
    void close(long runtimeGeneration, long commandId);
}
