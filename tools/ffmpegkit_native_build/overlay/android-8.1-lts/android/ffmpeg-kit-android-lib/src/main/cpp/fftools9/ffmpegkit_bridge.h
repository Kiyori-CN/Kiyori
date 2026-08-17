/*
 * FFmpegKit integration bridge for the embedded FFmpeg command tools.
 *
 * This file keeps the JNI-facing execution identity, cancellation epoch and
 * statistics callback outside FFmpeg's upstream command sources.  The command
 * sources still own their normal cleanup paths; the bridge only supplies the
 * process-hosting state that a reusable JNI library needs.
 */

#ifndef FFTOOLS_FFMPEGKIT_BRIDGE_H
#define FFTOOLS_FFMPEGKIT_BRIDGE_H

#include <stdarg.h>
#include <stdint.h>

#define FFMPEGKIT_MAX_LOG_SINKS 4

typedef void (*FFmpegKitLogCallback)(void *ptr,
                                     int level,
                                     const char *format,
                                     va_list vargs);

typedef struct FFmpegKitLogSink {
    FFmpegKitLogCallback callback;
    void *opaque;
    int replace_base;
} FFmpegKitLogSink;

typedef struct FFmpegKitExecutionContext {
    long session_id;
    unsigned long cancel_all_epoch;
    const char *program_name;
    int program_birth_year;
    void *tool_state;
    void (*tool_state_bind)(void *tool_state);
    FFmpegKitLogSink log_sinks[FFMPEGKIT_MAX_LOG_SINKS];
    unsigned int log_sink_count;
} FFmpegKitExecutionContext;

typedef void (*FFmpegKitReportCallback)(int frame_number,
                                        float fps,
                                        float quality,
                                        int64_t size,
                                        double time,
                                        double bitrate,
                                        double speed);

extern __thread long globalSessionId;
extern __thread const char *program_name;
extern __thread int program_birth_year;

int ffmpeg_execute(int argc, char **argv);
int ffprobe_execute(int argc, char **argv);

void ffmpegkit_execution_begin(long session_id);
void ffmpegkit_execution_bind(const FFmpegKitExecutionContext *context);
void ffmpegkit_execution_end(void);
const FFmpegKitExecutionContext *ffmpegkit_execution_context(void);
void ffmpegkit_execution_set_tool_state(void *tool_state,
                                        void (*tool_state_bind)(void *tool_state));

void ffmpegkit_set_program(const char *name, int birth_year);
void ffmpegkit_restore_log_callback(void);
void ffmpegkit_log_add_sink(FFmpegKitLogCallback callback, void *opaque);
void ffmpegkit_log_set_replacement(FFmpegKitLogCallback callback,
                                   void *opaque);
void *ffmpegkit_log_sink_opaque(void);
void ffmpegkit_log_dispatch_callback(void *ptr,
                                     int level,
                                     const char *format,
                                     va_list vargs);
void ffmpegkit_log_callback_function(void *ptr,
                                     int level,
                                     const char *format,
                                     va_list vargs);

int ffmpegkit_cancel_requested(const void *opaque);
void cancel_operation(long session_id);

void set_report_callback(FFmpegKitReportCallback callback);
void ffmpegkit_forward_report(uint64_t frame_number,
                              float fps,
                              float quality,
                              int64_t total_size,
                              int64_t pts,
                              double bitrate,
                              double speed);

void show_help_default_ffmpeg(const char *opt, const char *arg);
void show_help_default_ffprobe(const char *opt, const char *arg);

#endif /* FFTOOLS_FFMPEGKIT_BRIDGE_H */
