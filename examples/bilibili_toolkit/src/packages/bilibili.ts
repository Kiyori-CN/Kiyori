/* METADATA
{
  "name": "bilibili",
  "display_name": {
    "zh": "哔哩哔哩",
    "en": "Bilibili"
  },
  "description": {
    "zh": "检索哔哩哔哩视频、字幕、弹幕、评论和账号只读数据，并按用户要求导出媒体与分析产物。",
    "en": "Read-only Bilibili video, subtitle, danmaku, comment, and account research with user-requested media and analysis exports."
  },
  "enabledByDefault": false,
  "category": "Media",
  "tools": [
    { "name": "bilibili_doctor", "description": { "zh": "检查 Bilibili 登录状态和宿主媒体能力。", "en": "Check Bilibili login state and host media capabilities." }, "parameters": [] },
    { "name": "bilibili_resolve", "description": { "zh": "解析 Bilibili 视频、番剧或短链接目标。", "en": "Resolve a Bilibili video, season, episode, or short URL target." }, "parameters": [{ "name": "target", "description": { "zh": "Bilibili URL、BV/av、EP、SS 或 MD 标识。", "en": "Bilibili URL or BV/av, EP, SS, or MD identifier." }, "type": "string", "required": true }, { "name": "part", "description": { "zh": "可选分P编号。", "en": "Optional page number." }, "type": "number", "required": false }] },
    { "name": "bilibili_search", "description": { "zh": "搜索 Bilibili 视频。", "en": "Search Bilibili videos." }, "parameters": [{ "name": "keyword", "description": { "zh": "搜索关键词。", "en": "Search keyword." }, "type": "string", "required": true }, { "name": "page", "description": { "zh": "页码。", "en": "Page number." }, "type": "number", "required": false }, { "name": "pages", "description": { "zh": "页数。", "en": "Number of pages." }, "type": "number", "required": false }, { "name": "limit", "description": { "zh": "结果上限。", "en": "Result limit." }, "type": "number", "required": false }] },
    { "name": "bilibili_info", "description": { "zh": "获取视频信息。", "en": "Get video information." }, "parameters": [{ "name": "target", "description": { "zh": "目标。", "en": "Target." }, "type": "string", "required": true }, { "name": "part", "description": { "zh": "可选分P编号。", "en": "Optional page number." }, "type": "number", "required": false }] },
    { "name": "bilibili_season", "description": { "zh": "获取番剧或影视季信息。", "en": "Get season information." }, "parameters": [{ "name": "target", "description": { "zh": "EP、SS 或 MD 目标。", "en": "EP, SS, or MD target." }, "type": "string", "required": true }] },
    { "name": "bilibili_subtitles", "description": { "zh": "获取并导出字幕。", "en": "Fetch and export subtitles." }, "parameters": [{ "name": "target", "description": { "zh": "目标。", "en": "Target." }, "type": "string", "required": true }] },
    { "name": "bilibili_danmaku", "description": { "zh": "获取并导出当前弹幕。", "en": "Fetch and export current danmaku." }, "parameters": [{ "name": "target", "description": { "zh": "目标。", "en": "Target." }, "type": "string", "required": true }] },
    { "name": "bilibili_comments", "description": { "zh": "获取并导出评论。", "en": "Fetch and export comments." }, "parameters": [{ "name": "target", "description": { "zh": "目标。", "en": "Target." }, "type": "string", "required": true }] },
    { "name": "bilibili_user", "description": { "zh": "获取用户公开信息。", "en": "Get public user information." }, "parameters": [{ "name": "user", "description": { "zh": "用户 UID 或空间 URL。", "en": "User UID or space URL." }, "type": "string", "required": true }] },
    { "name": "bilibili_account", "description": { "zh": "获取当前账号的只读列表。", "en": "Read current-account lists." }, "parameters": [{ "name": "kind", "description": { "zh": "列表类型。", "en": "List type." }, "type": "string", "required": true }] },
    { "name": "bilibili_interactive", "description": { "zh": "导出互动视频图。", "en": "Export an interactive-video graph." }, "parameters": [{ "name": "target", "description": { "zh": "目标。", "en": "Target." }, "type": "string", "required": true }] },
    { "name": "bilibili_summary", "description": { "zh": "请求站内 AI 摘要。", "en": "Request the site AI summary." }, "parameters": [{ "name": "target", "description": { "zh": "目标。", "en": "Target." }, "type": "string", "required": true }] },
    { "name": "bilibili_formats", "description": { "zh": "列出可用媒体格式。", "en": "List available media formats." }, "parameters": [{ "name": "target", "description": { "zh": "目标。", "en": "Target." }, "type": "string", "required": true }] },
    { "name": "bilibili_download", "description": { "zh": "下载并处理媒体，使用 Kiyori 内置 FFmpeg。", "en": "Download and process media using Kiyori built-in FFmpeg." }, "parameters": [{ "name": "target", "description": { "zh": "目标。", "en": "Target." }, "type": "string", "required": true }] },
    { "name": "bilibili_frames", "description": { "zh": "从本地媒体提取关键帧。", "en": "Extract keyframes from local media." }, "parameters": [{ "name": "input_path", "description": { "zh": "媒体路径。", "en": "Media path." }, "type": "string", "required": true }] },
    { "name": "bilibili_capture", "description": { "zh": "一次性抓取视频信息及所选附加产物。", "en": "Capture video information and selected artifacts in one run." }, "parameters": [{ "name": "target", "description": { "zh": "目标。", "en": "Target." }, "type": "string", "required": true }] }
  ]
}
*/

import {
  account,
  capture,
  comments,
  danmaku,
  doctor,
  download,
  formats,
  frames,
  info,
  interactive,
  resolve,
  search,
  season,
  subtitles,
  summary,
  user
} from "../lib/operations";

export {
  account as bilibili_account,
  capture as bilibili_capture,
  comments as bilibili_comments,
  danmaku as bilibili_danmaku,
  doctor as bilibili_doctor,
  download as bilibili_download,
  formats as bilibili_formats,
  frames as bilibili_frames,
  info as bilibili_info,
  interactive as bilibili_interactive,
  resolve as bilibili_resolve,
  search as bilibili_search,
  season as bilibili_season,
  subtitles as bilibili_subtitles,
  summary as bilibili_summary,
  user as bilibili_user
};
