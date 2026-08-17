/* METADATA
{
    "name": "ffmpeg",

    "display_name": {
        "zh": "FFmpeg 工具集",
        "en": "FFmpeg Toolkit"
    },
    "description": {
        "zh": "提供FFmpeg工具，用于处理多媒体内容。",
        "en": "FFmpeg utilities for processing multimedia content."
    },
    "enabledByDefault": true,
    "category": "Media",
    "tools": [
        {
            "name": "ffmpeg_execute",
            "description": { "zh": "执行自定义 FFmpeg 参数；这不是 Shell，不支持管道、重定向或命令链，也不要包含前缀 ffmpeg。", "en": "Execute custom FFmpeg arguments. This is not a shell: pipes, redirection, and command chains are unsupported; do not include the leading ffmpeg." },
            "parameters": [
                { "name": "command", "description": { "zh": "要执行的 FFmpeg 参数（不要包含前缀 ffmpeg 或 Shell 语法）", "en": "FFmpeg arguments to execute (do not include the leading ffmpeg or shell syntax)" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "ffmpeg_info",
            "description": { "zh": "获取FFmpeg系统信息，包括版本、构建配置和支持的编解码器。", "en": "Get FFmpeg system info, including version, build config, and supported codecs." },
            "parameters": []
        },
        {
            "name": "ffmpeg_convert",
            "description": { "zh": "使用简化参数转换视频文件。", "en": "Convert a video file using simplified parameters." },
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
            data: result.output
        };
    }
    async function ffmpeg_info() {
        const result = await Tools.FFmpeg.info();
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg info retrieved successfully." : "Failed to retrieve FFmpeg info.",
            data: result.output
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
            data: result.output
        };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    async function main() {
        console.log("--- FFmpeg Tools Test ---");
        console.log("\n[1/3] Testing ffmpeg_info...");
        const infoResult = await ffmpeg_info();
        console.log(JSON.stringify(infoResult, null, 2));
        console.log("\n[2/3] Testing ffmpeg_execute (example: getting help for a decoder)...");
        const executeResult = await ffmpeg_execute({ command: '-h decoder=h264' });
        console.log(JSON.stringify(executeResult, null, 2));
        console.log("\n[3/3] Testing ffmpeg_convert (example)...");
        console.log("Skipping ffmpeg_convert test as it requires input files.");
        complete({ success: true, message: "Test finished." });
    }
    return {
        ffmpeg_execute: (params) => wrapToolExecution(ffmpeg_execute, params),
        ffmpeg_info: (params) => wrapToolExecution(ffmpeg_info, params),
        ffmpeg_convert: (params) => wrapToolExecution(ffmpeg_convert, params),
        main,
    };
})();
exports.ffmpeg_execute = FFmpegTools.ffmpeg_execute;
exports.ffmpeg_info = FFmpegTools.ffmpeg_info;
exports.ffmpeg_convert = FFmpegTools.ffmpeg_convert;
exports.main = FFmpegTools.main;
