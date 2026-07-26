# 媒体 Intent 与播放器基础

## 根因

`MainActivity` 的 `ACTION_VIEW` wildcard filter 接收 `*/*`，非 HTTP/HTTPS URI 一律写入 `pendingSharedFileUris`，随后进入 `SharedFileHandler`。因此视频用 Kiyori 打开时被解释为 AI 附件。

## Intent 设计

- 新建独立播放器 Activity，显式接收 `video/*` 和经过审阅的流媒体 MIME/扩展
- `MainActivity` 的文件打开 filter 收窄到真实附件预览类型；`ACTION_SEND` 和 `ACTION_SEND_MULTIPLE` 继续属于聊天分享
- HTTP/HTTPS 普通链接继续进入 Browser Runtime；明确的视频链接只有用户选择播放器入口时进入播放器
- Activity 读取 `content://`、`file://` 和网络 URI，保留临时 URI 权限，不复制文件到聊天附件目录
- `onNewIntent` 更新现有播放会话，不创建重叠 player 实例

## 播放器架构

- 以旧 Kiyori `MpvCore`、`PlaybackEngine`、`VideoPlayerActivity` 和 `MpvSeamlessHandoff` 为行为来源
- 以 `mpv-android-anime4k` 的 libmpv、渲染参数和 Anime4K shader 选择为优化参考
- 建立唯一 `PlayerSession`，拥有 URI、headers、标题、时长、位置、暂停、倍速、音轨、字幕和渲染 surface
- 全屏 Activity 和后续悬浮播放器只切换 surface owner，不重新加载 media
- 用户已接受完整移植所需的 `GPL-3.0-or-later` 分发边界，但未授权公开发布；引入播放器依赖时同步补齐许可证正文、NOTICE、来源、版本、哈希、ABI 和对应源码义务
- 旧 `mpv-android-lib-v0.1.10.aar` 与当前 `ffmpeg-kit-local.aar` 含七个同名但内容不同的 `libav*.so`，禁止使用 packaging 选取规则掩盖冲突
- 必须构建或选定无重复动态库的统一 FFmpeg/libmpv 栈，并审计 APK 体积、全部 ABI 与 arm64 16KB `PT_LOAD`；不以 AAR 能编译替代兼容性证明

## 首期播放器 UI

- 黑色沉浸背景，顶部返回、标题和更多；中央播放/暂停；底部进度、时间、倍速、音轨、字幕和全屏控制
- 手势区支持左侧亮度、右侧音量、水平进度，手势开始后锁定轴向
- 手机横屏和大屏使用同一会话，不因配置变化重新加载
- 错误页显示具体错误、媒体 URI 类型和重试动作；错误必须记录日志

## 设置入口

播放器基础完成后才向 Settings Home 加入视频播放器入口。首期设置只包含真实 mpv 参数：硬件解码策略、默认倍速、后台/悬浮行为和 Anime4K 模式；每项必须直接约束 player owner。

## 验收

- 系统“用 Kiyori 打开”视频进入播放器，不进入 AI 附件
- 本地 content URI 和网络视频可开始、暂停、seek 和恢复
- Activity 重建和方向变化不重载媒体
- libmpv 依赖、ABI、许可证和 16KB 证据记录完整
- Debug APK 与本地门禁通过；提交、推送和远端 SHA 仅在用户另行授权时执行，真机解码和手势保持待验证
