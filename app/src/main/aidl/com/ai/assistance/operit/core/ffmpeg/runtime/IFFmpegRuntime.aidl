package com.ai.assistance.operit.core.ffmpeg.runtime;

import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeRequest;
import com.ai.assistance.operit.core.ffmpeg.runtime.IFFmpegRuntimeCallback;

interface IFFmpegRuntime {
    boolean registerCallback(long runtimeGeneration, IFFmpegRuntimeCallback callback);
    boolean submit(long runtimeGeneration, in FFmpegRuntimeRequest request);
    boolean cancel(long runtimeGeneration, String requestId);
}
