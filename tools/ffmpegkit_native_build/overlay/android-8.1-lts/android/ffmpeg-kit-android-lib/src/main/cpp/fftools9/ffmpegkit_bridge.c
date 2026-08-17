/*
 * FFmpegKit integration bridge for the embedded FFmpeg command tools.
 */

#include <stdatomic.h>
#include <stddef.h>

#include "libavutil/avassert.h"
#include "libavutil/avutil.h"
#include "libavutil/log.h"

#include "ffmpegkit_bridge.h"

extern void cancelSession(long session_id);
extern int cancelRequested(long session_id);
extern atomic_int redirectionEnabled;
extern void ffmpegkit_log_callback_function(void *ptr, int level,
                                            const char *format, va_list vargs);

__thread const char *program_name;
__thread int program_birth_year;

static __thread FFmpegKitExecutionContext current_execution;
static __thread void *current_log_sink_opaque;
static atomic_ulong cancel_all_epoch;
static _Atomic(FFmpegKitReportCallback) report_callback;

void ffmpegkit_execution_begin(long session_id)
{
    current_execution = (FFmpegKitExecutionContext) {
        .session_id = session_id,
        .cancel_all_epoch =
            atomic_load_explicit(&cancel_all_epoch, memory_order_acquire),
    };
    globalSessionId = session_id;
}

void ffmpegkit_execution_bind(const FFmpegKitExecutionContext *context)
{
    if (context) {
        current_execution = *context;
        globalSessionId = context->session_id;
        program_name = context->program_name;
        program_birth_year = context->program_birth_year;
        if (context->tool_state_bind)
            context->tool_state_bind(context->tool_state);
    } else {
        ffmpegkit_execution_end();
    }
}

void ffmpegkit_execution_end(void)
{
    current_execution = (FFmpegKitExecutionContext) {
        .cancel_all_epoch =
            atomic_load_explicit(&cancel_all_epoch, memory_order_acquire),
    };
    current_log_sink_opaque = NULL;
    globalSessionId = 0;
    program_name = NULL;
    program_birth_year = 0;
}

const FFmpegKitExecutionContext *ffmpegkit_execution_context(void)
{
    return &current_execution;
}

void ffmpegkit_execution_set_tool_state(void *tool_state,
                                        void (*tool_state_bind)(void *tool_state))
{
    current_execution.tool_state = tool_state;
    current_execution.tool_state_bind = tool_state_bind;
}

void ffmpegkit_set_program(const char *name, int birth_year)
{
    current_execution.program_name = name;
    current_execution.program_birth_year = birth_year;
    program_name = name;
    program_birth_year = birth_year;
}

void ffmpegkit_restore_log_callback(void)
{
    av_log_set_callback(ffmpegkit_log_dispatch_callback);
}

static void ffmpegkit_log_call_sink(const FFmpegKitLogSink *sink,
                                    void *ptr,
                                    int level,
                                    const char *format,
                                    va_list vargs)
{
    va_list sink_args;

    current_log_sink_opaque = sink->opaque;
    va_copy(sink_args, vargs);
    sink->callback(ptr, level, format, sink_args);
    va_end(sink_args);
    current_log_sink_opaque = NULL;
}

void ffmpegkit_log_add_sink(FFmpegKitLogCallback callback, void *opaque)
{
    FFmpegKitLogSink *sink;

    av_assert0(callback);
    for (unsigned int i = 0; i < current_execution.log_sink_count; i++) {
        sink = &current_execution.log_sinks[i];
        if (sink->callback == callback && sink->opaque == opaque &&
            !sink->replace_base)
            return;
    }
    av_assert0(current_execution.log_sink_count < FFMPEGKIT_MAX_LOG_SINKS);
    sink = &current_execution.log_sinks[current_execution.log_sink_count++];
    *sink = (FFmpegKitLogSink) {
        .callback = callback,
        .opaque = opaque,
        .replace_base = 0,
    };
}

void ffmpegkit_log_set_replacement(FFmpegKitLogCallback callback,
                                   void *opaque)
{
    FFmpegKitLogSink *sink;

    av_assert0(callback);
    for (unsigned int i = 0; i < current_execution.log_sink_count; i++) {
        sink = &current_execution.log_sinks[i];
        if (sink->callback == callback && sink->opaque == opaque &&
            sink->replace_base)
            return;
    }
    av_assert0(current_execution.log_sink_count < FFMPEGKIT_MAX_LOG_SINKS);
    sink = &current_execution.log_sinks[current_execution.log_sink_count++];
    *sink = (FFmpegKitLogSink) {
        .callback = callback,
        .opaque = opaque,
        .replace_base = 1,
    };
}

void *ffmpegkit_log_sink_opaque(void)
{
    return current_log_sink_opaque;
}

void ffmpegkit_log_dispatch_callback(void *ptr,
                                     int level,
                                     const char *format,
                                     va_list vargs)
{
    int has_replacement = 0;

    for (unsigned int i = 0; i < current_execution.log_sink_count; i++)
        has_replacement |= current_execution.log_sinks[i].replace_base;

    if (has_replacement) {
        for (unsigned int i = 0; i < current_execution.log_sink_count; i++) {
            const FFmpegKitLogSink *sink = &current_execution.log_sinks[i];

            if (sink->replace_base)
                ffmpegkit_log_call_sink(sink, ptr, level, format, vargs);
        }
        return;
    }

    {
        va_list base_args;

        va_copy(base_args, vargs);
        if (atomic_load_explicit(&redirectionEnabled, memory_order_acquire))
            ffmpegkit_log_callback_function(ptr, level, format, base_args);
        else
            av_log_default_callback(ptr, level, format, base_args);
        va_end(base_args);
    }

    for (unsigned int i = 0; i < current_execution.log_sink_count; i++)
        ffmpegkit_log_call_sink(&current_execution.log_sinks[i],
                                ptr, level, format, vargs);
}

int ffmpegkit_cancel_requested(const void *opaque)
{
    const FFmpegKitExecutionContext *context = opaque;

    if (!context)
        context = &current_execution;

    if (context->session_id && cancelRequested(context->session_id))
        return 1;

    return context->cancel_all_epoch !=
           atomic_load_explicit(&cancel_all_epoch, memory_order_acquire);
}

void cancel_operation(long session_id)
{
    if (session_id == 0) {
        atomic_fetch_add_explicit(&cancel_all_epoch, 1, memory_order_acq_rel);
    } else {
        cancelSession(session_id);
    }
}

void set_report_callback(FFmpegKitReportCallback callback)
{
    atomic_store_explicit(&report_callback, callback, memory_order_release);
}

void ffmpegkit_forward_report(uint64_t frame_number,
                              float fps,
                              float quality,
                              int64_t total_size,
                              int64_t pts,
                              double bitrate,
                              double speed)
{
    FFmpegKitReportCallback callback =
        atomic_load_explicit(&report_callback, memory_order_acquire);
    double milliseconds;

    if (!callback)
        return;

    milliseconds = pts == AV_NOPTS_VALUE ? 0.0 : pts / 1000.0;
    callback((int)frame_number, fps, quality, total_size, milliseconds,
             bitrate, speed);
}
