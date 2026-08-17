/**
 * FFmpeg type definitions for Assistance Package Tools
 */

import { FFmpegResultData } from './results';

/** Qualified deterministic conversion profiles. */
export type FFmpegConversionProfile = 'h264_aac_mp4';

export type FFmpegResolution =
    | '1280x720'   // HD
    | '1920x1080'  // Full HD
    | '3840x2160'  // 4K
    | '7680x4320'  // 8K
    | `${number}x${number}`;  // Custom resolution

export type FFmpegVideoBitrate =
    | '500k'       // 500 kbps
    | '1000k'      // 1000 kbps
    | '2000k'      // 2000 kbps
    | '4000k'      // 4000 kbps
    | '8000k'      // 8000 kbps
    | `${number}k` // Custom kbps
    | `${number}M`; // Custom Mbps

/**
 * FFmpeg operations namespace
 */
export namespace FFmpeg {
    /**
     * Execute a custom FFmpeg command
     * @param command - FFmpeg command arguments only (do not include the leading ffmpeg)
     * @returns Promise resolving to FFmpegResultData containing execution details
     * @throws Error if the command execution fails
     */
    function execute(command: string): Promise<FFmpegResultData>;

    /**
     * Get FFmpeg system information
     * @returns Promise resolving to FFmpegResultData containing system information
     */
    function info(): Promise<FFmpegResultData>;

    /**
     * Convert video file with simplified parameters
     * @param inputPath - Source video file path
     * @param outputPath - Destination video file path
     * @param options - Optional conversion parameters
     */
    function convert(
        inputPath: string,
        outputPath: string,
        options?: {
            profile?: FFmpegConversionProfile;
            resolution?: FFmpegResolution;
            video_bitrate?: FFmpegVideoBitrate;
        }
    ): Promise<FFmpegResultData>;
}
