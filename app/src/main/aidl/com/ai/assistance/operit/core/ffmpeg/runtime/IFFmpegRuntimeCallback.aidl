package com.ai.assistance.operit.core.ffmpeg.runtime;

import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeFailure;
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeResult;

oneway interface IFFmpegRuntimeCallback {
    void onRequestStarted(
        long runtimeGeneration,
        long eventSequence,
        String requestId,
        long sessionId,
        int processId
    );
    void onRequestCompleted(
        long runtimeGeneration,
        long eventSequence,
        in FFmpegRuntimeResult result
    );
    void onRequestFailed(
        long runtimeGeneration,
        long eventSequence,
        in FFmpegRuntimeFailure failure
    );
}
