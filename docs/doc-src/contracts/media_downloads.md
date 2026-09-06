# 媒体、资源目录与下载契约

适用于网页资源、媒体候选、内部下载、mpv 会话与播放设置。详细 native、缓存及进程契约见 [播放器架构](../dev-core/PLAYER_ARCHITECTURE.md) 和 [FFmpeg 架构](../dev-core/FFMPEG_ARCHITECTURE.md)。

## 资源与候选

- 每 WebSession 持有当前 document generation 的静态资源目录与有界媒体候选。资源按原始 URL 归一化聚合：去 fragment、保留 query、统一默认端口和 host/scheme 大小写，上限 2,000 条。
- 重复请求累加 `requestCount` 并保留首末证据；目录按视频、音频、图片、网页、脚本、样式、数据、字体与其他分组，不当作逐事件历史。
- 媒体身份依赖真实证据：video DOM 高于误导后缀或音频 MIME；音频和视频 DOM 均被观察。单纯扩展名不能证明解码器能力。
- 请求线程使用 `appliedUserAgent` 快照、WebResourceRequest 与 Profile Cookie，不在 Chromium worker 上调用 WebView API。
- 候选播放使用同一 PlayerSession，下载使用同一 BrowserDownloadManager；不存在第二个媒体下载或播放入口状态源。

## 图片查看

- 图片请求采用观察到的头及该 Profile 缺失的 UA、Cookie、Referer；全屏查看冻结输入快照，用 `ContentScale.Fit` 展示，横向分页，底部显示页码和保存。
- 支持 `1x..5x` 双指缩放、点击退出、长按保存原图和垂直拖动退出；背景退出时揭示真实 Browser。
- 网络目录使用当前筛选的 HTTP(S) 图片，元素全屏查看使用单图；网页看图模式最多扫描 2,000 个元素、去重并保留 200 张，定位到选中图。
- 保存先关闭查看器，再调用唯一下载所有者。

## 下载任务

- 负一屏与 Browser 抽屉显示同一 manager 快照，查询、排序、过滤与选择只是展示状态；系统所有任务留在 Android 下载 UI。
- 设置包含引擎、目录、并发、普通/M3U8 线程、网络、漫游、通知、系统通知入口、离线包装、chunk、APK 清理、确认和 HTTP 协议。每任务创建时冻结相关设置与路由。
- 并发范围随线程数同步缩小：`128 / max(normalThreads, m3u8Threads)`，上限 8。
- 批量取消只对 `canCancel` 项调用取消，不删除记录或文件；批量删除明确区分仅记录与记录加文件。全选只作用于当前可见且符合该动作的任务。
- 手动 HTTP(S) 下载显式选择内部引擎或系统 DownloadManager；Browser 来源保留 Profile 请求身份，负一屏可独立请求。
- 已完成网络任务可重新下载；路径型 M3U8 通过 FFmpeg 重封装后，原记录原子切换为 MP4。
- SAF 目录变更使用专用透明 ComponentActivity；设置或文件移动失败时，未被其他设置/任务引用的授权及时释放。
- APK 自动清理记录精确 package/version，只有匹配且受系统保护的安装广播到达后才删除。

## PlayerSession 与控制

- `PlayerSettingsStore` 持有解码、渲染、速度、seek、队列、缓存、字幕、后台、旋转、Anime4K、音量和 SAF 输出设置；UI 不直接写 mpv 属性。
- 原生运行时仅在非导出的 `:player`；主进程 PlayerSession 持有加载、请求、队列与展示。崩溃进入可见 `DEAD`，用户明确重启前不自动连接另一内核。
- 手势检测器不随高频播放位置重建。点击切换控制、锁定点击显示解锁、双击与长按/拖动属于同一序列；自动隐藏只在播放且没有弹层、seek 或手势时计时。
- 长按加速按原速度选择 `1x / 2x / 3x`，松手恢复原值；反馈各显示一秒，临时速度不写速度记忆。
- 全屏和悬浮进度条只维护拖动草稿，结束提交一次 seek，取消不提交。PlayerSession 立即投影目标，旧进度快照不能拉回滑块。
- 每 load 最多一个原生 seek 在途，后续输入合并为最新目标；只有匹配 `MPV_EVENT_SEEK → MPV_EVENT_PLAYBACK_RESTART` 才消费。Surface 重配置 seek 不占用未来用户目标。
- 新媒体、关闭、死亡、命令失败清理 pending。普通 seek 用 `absolute+keyframes`，精确模式用 `absolute+exact` 与 `hr-seek-framedrop=yes`。
- 准备指示延迟 160ms，避免快速首帧闪烁。重力旋转使用 `FULL_SENSOR`，开启后禁用冲突的手动方向动作；弹幕无实际所有者时明确禁用。

## 缓存与外部打开

- 单一网络缓存策略为“省流 / 智能均衡 / 流畅优先 / 完整缓存”，新安装默认智能均衡；打开下一媒体时快照，播放中设置变更不替换当前请求所有者。
- 完整缓存使用同一 mpv 请求与 app-private 会话目录，资格、20 GiB 上限、空间余量、完成证明与清理边界详见播放器架构。它不是离线下载。
- “默认视频播放器”只影响外部 `ACTION_VIEW` 视频文件；Browser 候选、队列、悬浮/全屏继续使用唯一 Kiyori PlayerSession。

## 验收边界

静态构建与 JVM 测试不能证明 OEM Surface、手势、真机网络、解码、旋转或 SAF 行为。对应专项保持原有设备验收状态。
