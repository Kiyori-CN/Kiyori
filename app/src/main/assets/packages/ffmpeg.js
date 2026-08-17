/* METADATA
{
    "name": "ffmpeg",

    "display_name": {
        "zh": "FFmpeg 工具集",
        "en": "FFmpeg Toolkit"
    },
    "description": {
        "zh": "调用 Android com.kiyori:ffmpeg 中的 FFmpegKit/FFprobe；不调用 Ubuntu 或播放器 FFmpeg。",
        "en": "Use FFmpegKit and FFprobe in Android com.kiyori:ffmpeg; never invoke Ubuntu or player FFmpeg."
    },
    "enabledByDefault": true,
    "category": "Media",
    "tools": [
        {
            "name": "ffmpeg_execute",
            "description": { "zh": "在 Android com.kiyori:ffmpeg 中原样执行 FFmpeg 参数；不是 Shell，拒绝管道、重定向或命令链，不调用 Ubuntu/播放器 FFmpeg；drawtext 需绝对 fontfile。", "en": "Execute raw FFmpeg arguments in Android com.kiyori:ffmpeg. This is not a shell. Shell syntax is rejected, Ubuntu/player FFmpeg is never invoked, and drawtext needs an absolute fontfile." },
            "parameters": [
                { "name": "command", "description": { "zh": "要执行的 FFmpeg 参数（不要包含前缀 ffmpeg 或 Shell 语法）", "en": "FFmpeg arguments to execute (do not include the leading ffmpeg or shell syntax)" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "ffmpeg_info",
            "description": { "zh": "查询 Android FFmpegKit 的指定能力分区，不需要 Shell 管道。", "en": "Query one Android FFmpegKit capability section without shell pipes." },
            "parameters": [
                { "name": "section", "description": { "zh": "可选：summary/codecs/encoders/decoders/filters/formats/muxers/demuxers/protocols/hwaccels/buildconf", "en": "Optional: summary/codecs/encoders/decoders/filters/formats/muxers/demuxers/protocols/hwaccels/buildconf" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "ffmpeg_probe",
            "description": { "zh": "使用 Android com.kiyori:ffmpeg 中的 FFprobe 探测媒体并返回结构化信息。", "en": "Probe media with FFprobe in Android com.kiyori:ffmpeg and return structured metadata." },
            "parameters": [
                { "name": "input_path", "description": { "zh": "存在且非空的媒体文件绝对路径", "en": "Absolute path to an existing non-empty media file" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "ffmpeg_convert",
            "description": { "zh": "使用 Android 资格化转换管线转码，并由同一执行面的 FFprobe 验证后原子提交。", "en": "Transcode with the qualified Android pipeline, verify with same-plane FFprobe, then commit atomically." },
            "parameters": [
                { "name": "input_path", "description": { "zh": "源视频文件路径", "en": "Input video file path" }, "type": "string", "required": true },
                { "name": "output_path", "description": { "zh": "目标 MP4 文件的绝对路径；文件必须不存在", "en": "Absolute destination MP4 path; the file must not already exist" }, "type": "string", "required": true },
                { "name": "profile", "description": { "zh": "可选，确定性转换配置；当前仅支持 h264_aac_mp4", "en": "Optional deterministic conversion profile; currently only h264_aac_mp4" }, "type": "string", "required": false },
                { "name": "resolution", "description": { "zh": "可选，16 到 8192 范围内的偶数宽高，例如 1280x720", "en": "Optional even dimensions from 16 to 8192, e.g. 1280x720" }, "type": "string", "required": false },
                { "name": "video_bitrate", "description": { "zh": "可选，64k 到 100M 的视频比特率，例如 4000k 或 4M", "en": "Optional video bitrate from 64k to 100M, e.g. 4000k or 4M" }, "type": "string", "required": false }
            ]
        }
    ]
}*/
const FFmpegTools = (function () {
    async function ffmpeg_execute(params) {
        const result = await Tools.FFmpeg.execute(params.command);
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg command executed successfully." : `FFmpeg command failed with return code ${result.returnCode}.`,
            data: result
        };
    }
    async function ffmpeg_info(params) {
        const result = await Tools.FFmpeg.info(params.section);
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg info retrieved successfully." : "Failed to retrieve FFmpeg info.",
            data: result
        };
    }
    async function ffmpeg_probe(params) {
        const result = await Tools.FFmpeg.probe(params.input_path);
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "Android FFprobe completed successfully." : `Android FFprobe failed with return code ${result.returnCode}.`,
            data: result
        };
    }
    async function ffmpeg_convert(params) {
        const result = await Tools.FFmpeg.convert(params.input_path, params.output_path, {
            profile: params.profile,
            resolution: params.resolution,
            video_bitrate: params.video_bitrate,
        });
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg conversion completed successfully." : `FFmpeg conversion failed with return code ${result.returnCode}.`,
            data: result
        };
    }
    function isToolExecutionError(error) {
        return ('message' in error &&
            typeof error.message === 'string');
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            let message;
            let data;
            if (typeof error === 'object' && error !== null && isToolExecutionError(error)) {
                message = error.message;
                data = error.data;
            }
            else {
                message = typeof error === 'string'
                    ? error
                    : "FFmpeg tool execution failed without a structured error.";
                data = undefined;
            }
            console.error(`Tool ${func.name} failed: ${message}`);
            complete({
                success: false,
                message,
                data,
            });
        }
    }
    async function main() {
        console.log("--- FFmpeg Tools Test ---");
        console.log("\n[1/4] Testing ffmpeg_info...");
        const infoResult = await ffmpeg_info({ section: 'summary' });
        console.log(JSON.stringify(infoResult, null, 2));
        console.log("\n[2/4] Testing ffmpeg_execute (example: getting help for a decoder)...");
        const executeResult = await ffmpeg_execute({ command: '-h decoder=h264' });
        console.log(JSON.stringify(executeResult, null, 2));
        console.log("\n[3/4] Testing ffmpeg_probe requires an input file and is skipped.");
        console.log("\n[4/4] Testing ffmpeg_convert (example)...");
        console.log("Skipping ffmpeg_convert test as it requires input files.");
        complete({ success: true, message: "Test finished." });
    }
    return {
        ffmpeg_execute: (params) => wrapToolExecution(ffmpeg_execute, params),
        ffmpeg_info: (params) => wrapToolExecution(ffmpeg_info, params),
        ffmpeg_probe: (params) => wrapToolExecution(ffmpeg_probe, params),
        ffmpeg_convert: (params) => wrapToolExecution(ffmpeg_convert, params),
        main,
    };
})();
exports.ffmpeg_execute = FFmpegTools.ffmpeg_execute;
exports.ffmpeg_info = FFmpegTools.ffmpeg_info;
exports.ffmpeg_probe = FFmpegTools.ffmpeg_probe;
exports.ffmpeg_convert = FFmpegTools.ffmpeg_convert;
exports.main = FFmpegTools.main;
