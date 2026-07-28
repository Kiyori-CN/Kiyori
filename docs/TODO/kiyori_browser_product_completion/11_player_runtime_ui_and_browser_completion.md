# 播放器运行时、界面与浏览器播放收口

## 本轮定位

阶段 8 与阶段 9 已经建立唯一 `PlayerSession`、统一 FFmpeg/libmpv 栈、媒体 Intent、浏览器 candidate
和同会话悬浮/全屏转挂。本轮不推翻这些所有权，而是针对真实运行时反馈和直接源码对照完成第二次收口：

- 根治 `java.lang.NoClassDefFoundError: is.xyz.mpv.MPVLib`
- 把播放器源文件拆成清晰的 session、mpv engine、媒体解析、Surface 和 Compose UI 职责
- 让全屏播放器的控件层级、尺寸和位置对齐 `kiyori-android@24a2dfa9`
- 扩展只有 `PlayerSession` 会真实消费的播放器设置
- 补齐浏览器候选数字圆圈、自动小窗和网络日志在线播放

本轮仍只允许本地修改、测试与 Debug APK。提交、推送、发布、安装设备、ADB、MuMu、Release 和部署均不在
授权范围内。

## 本地结果

- [DONE] `shouldInterceptRequest` 候选采集使用 volatile WebSession UA 快照，用户提供的
  `ThreadPoolForeg` / `WebView.getSettings()` 崩溃路径已移除并有静态门禁
- [DONE] `MpvPlayerEngine` 是唯一 MPV binding import owner，全部 JNI 入口把 `LinkageError` 转为可见错误；
  自然结束使用 `eof-reached`，不会把 replace/stop 的 `END_FILE` 误判成播完
- [DONE] 播放器默认横屏、浏览器悬浮后台行为、Android 26-28 截图保存、累计亮度/音量手势和候选精确
  下载/网络日志播放已收口
- [DONE] 全量 JVM `555/555`、本轮 native Python `18/18`、formal readiness、`git diff --check` 与 Debug 构建通过
- [DONE] 用户真机报告的 Clang 21 `libmpv.so` 与 Clang 18 C++ runtime 符号不匹配已修正；最终 APK 中
  121 个唯一 C++ 符号缺失 0，现场两个 `__from_chars_floating_point` 导出均存在
- [DONE] 最终 APK 时间 `2026-07-28 10:59:29 +08:00`，大小 `468740360`，SHA-256
  `44642BF9A4EE713D2566B6FEF2E77865067E7CB2D030F705D139075003B1ED03`；native/许可证证据见阶段 10 与
  `PLAYER_NATIVE_STACK.md`
- [PENDING] 安装该新 APK 后的真实播放复测、Anime4K 性能/温度、手势、截图、后台、自动小窗、
  悬浮/全屏往返和站点兼容
  仍为 `verification_pending`

## 固定参考与差异

### `kiyori-android@24a2dfa9`

采用：

- `activity_video_player.xml` 的顶部信息栏、底部进度和中央按钮组、两侧手势反馈、锁定与超分入口
- `BrowserTopBar` 内 28dp 橙色视频数量圆圈和 `99+` 上限
- `BrowserFloatingSniffPolicy` 的“设置开启、候选稳定后自动小窗、关闭后本页不重复启动”语义
- `VideoSnifferManager` 与 `UrlDetector` 对 HLS、DASH、普通视频和 DOM 来源分层展示的思路

拒绝：

- `MpvSeamlessHandoff` 在多个 `PlaybackEngine` 之间传递 engine 的临时 owner 模型
- handoff 失败后重新 `loadRemote`、代理播放、URL resolver 和其他重载路径
- 浏览器小窗前后调用 JavaScript pause/resume 网页媒体
- MIME、探测结果或解析器输出替换原始媒体 URL

### `mpv-android-anime4k@32f5f169`

采用：

- `MPVLib.create -> setOptionString -> init` 的初始化顺序
- 精确 seek、缓存上限、字幕样式和实时 `glsl-shaders` 参数
- Anime4K shader 延迟复制、模式链和失败可见语义

拒绝：

- `mediacodec,mediacodec-copy,no` 形式的隐式解码降级链
- `tls-verify=no`
- 独立媒体库、播放历史、下载数据库和第二播放器导航

### `hikerView@5de88090`

采用：

- Range、原始请求 headers、HLS manifest 与普通文件下载必须保持同一请求身份
- 网络日志和下载动作不应只保存裸 URL

拒绝：

- 本地 HTTP 代理缓存、URL 重写、M3U8 二次解析和第二套缓存任务 owner
- 把接口 MIME 或响应猜测直接改写为可播放文件

## 运行时修复设计

`MPVLib` 薄 AAR 必须成为显式 Gradle 依赖，不再依赖 `app/libs/*.aar` 通配符的隐式发现。构建前检查不仅核对
AAR member 和 native 哈希，还必须打开 `classes.jar` 并确认：

- `is/xyz/mpv/MPVLib.class`
- `is/xyz/mpv/MPVLib$EventObserver.class`
- `is/xyz/mpv/MPVNode.class`

Debug APK 构建后必须扫描全部 `classes*.dex`，确认 `Lis/xyz/mpv/MPVLib;`、`Lis/xyz/mpv/MPVNode;` 和
Kiyori 的 mpv engine descriptor 同时存在。该门禁与 `libmpv.so`、`libplayer.so`、`DT_NEEDED`、ABI、符号和
16 KB `PT_LOAD` 审计并列，不能用编译成功代替。

真机在 `2026-07-28 10:06` 暴露了另一个独立装载错误：Clang 21 构建的 `libmpv.so` 引用 float/double
两个 `std::__ndk1::__from_chars_floating_point`，而先前由 Clang 18 FFmpegKit 输入提供的
`libc++_shared.so` 不导出它们。修正后的 mpv AAR 必须保留固定 mpv 输入中的同工具链 C++ runtime；
FFmpegKit 输入确定性转换为只含 arm64 Java/资源/许可证与九个 FFmpeg native 库的 AAR。构建前和最终
APK 门禁必须同时确认唯一 C++ runtime、两个符号与 16 KB 对齐。

`PlayerSession` 不再直接实现 `MPVLib.EventObserver`。唯一 session 私有持有一个 `MpvPlayerEngine`，所有
`MPVLib` 类型只出现在 engine 文件中。engine 创建时若发生 `ClassNotFoundException`、
`NoClassDefFoundError`、`UnsatisfiedLinkError` 或其他 `LinkageError`，session 进入包含具体原因的错误状态，
不得崩溃 Activity，也不得创建替代播放器或重试另一个内核。正确 APK 中该路径必须由 DEX/native 门禁证明
不会因为缺少打包输入而触发。

## 唯一会话与文件职责

```text
core/player/
	PlayerModels.kt
	PlayerSettingsStore.kt
	PlayerSession.kt
	MpvPlayerEngine.kt
	PlayerMediaResolver.kt
	Anime4KShaderManager.kt
ui/features/player/
	PlayerActivity.kt
	PlayerScreen.kt
	PlayerControls.kt
	PlayerGestureLayer.kt
	PlayerSurfaceView.kt
```

- `PlayerSession`：唯一状态机、request、文件描述符、设置快照、presentation 和 surface owner
- `MpvPlayerEngine`：唯一 `MPVLib` observer、core 初始化、mpv property/command 和 Surface 调用
- `PlayerMediaResolver`：原样解析 `content://`、`file://`、裸本地路径和允许的网络 scheme
- `PlayerActivity`：Intent、系统栏、生命周期和返回语义
- `PlayerScreen`、`PlayerControls`、`PlayerGestureLayer`：只消费 state 和 session command

任何拆分都不能增加第二个 core、第二份状态或可绕过 session 的 UI 直写入口。

## 播放器界面

以旧版布局为视觉权威，首期真实消费者包括：

- 顶部：返回、两行标题、实时网络速度、电量与时间、字幕、画面比例、更多
- 底部：时间位于进度条上方；中央依次为上一项禁用、后退、播放/暂停、前进、下一项禁用、倍速
- 底部两侧：Anime4K 与旋转
- 右侧：截图、锁定、下载；只有来源为浏览器 candidate 时下载可用
- 中央与两侧：加载、seek、亮度、音量、双击前后跳和锁定反馈

旧版弹幕和投屏依赖当前项目没有的完整真实 owner，本轮不显示伪按钮，也不添加空点击。上一项和下一项维持
旧版单媒体会话的禁用状态。按钮图标、尺寸、间距和相对位置仍按固定布局对齐。

## 真实设置

`PlayerSettingsStore` 新增以下字段，全部由唯一 session 或 UI command 消费：

- 精确 seek：约束 `hr-seek`、`hr-seek-framedrop` 和 seek command mode
- 快进/快退时长：约束按钮、双击和横向手势的步长
- 网络缓存：约束 `demuxer-max-bytes`、`demuxer-max-back-bytes` 与 `cache-secs`
- 字幕缩放：约束 mpv `sub-scale`
- 播放结束行为：暂停保留或关闭会话，约束 `loop-file` 与 `eof-reached` 自然结束处理；普通
  `MPV_EVENT_END_FILE` 不得把 replace/stop 误判成播放完成

既有硬件解码、默认倍速、后台行为、全屏返回和 Anime4K 继续保留。设置页继续复用
`KiyoriCollapsingSettingsPage` 与 `KiyoriSettingsGroupCard`，不建立平行视觉系统。

## 浏览器收口

- 顶部搜索框右侧在候选数大于零时显示 28dp 橙色圆圈，点击直接打开当前 WebSession 的候选抽屉
- 自动小窗只选择 `directPlaybackReady=true` 的原始 URL；优先 DOM play/currentSrc、HLS/DASH 和普通视频文件
- 候选变化先短暂去抖再选择，同一页面关闭后不重复启动；全屏和悬浮交接期间不选择新媒体
- 自动小窗不执行网页 JavaScript，不暂停网页媒体，不主动 probe，不解析或替换 URL
- 网络日志只有在条目 URL 与当前 direct candidate 精确相同时显示“播放”；播放和下载都复用 candidate ID，
  从而保留 headers、Cookie scope 和原始 URL
- MIME-only 接口和 blob/MSE 的播放、下载保持禁用
- `shouldInterceptRequest` 只读取 `WebResourceRequest`、CookieManager 和 volatile WebSession 快照，严禁从该
  Chromium 工作线程调用 `WebView.settings`；用户提供的 `ThreadPoolForeg` 崩溃由此门禁覆盖

## 验收

1. AAR class、显式依赖和最终 DEX descriptor 门禁通过
2. `PlayerSession` 状态机、设置映射、seek/结束/画面比例和 engine 事件桥测试通过
3. 顶栏 candidate badge、自动选择、关闭抑制、网络日志 candidate 映射测试通过
4. 源码静态门禁确认 WebView 后台线程零访问、无网页媒体 JavaScript、无播放器回退、无 native 选取规则
5. formal readiness、`git diff --check` 和 `:app:assembleDebug` 通过
6. 最终 APK 重新核对 DEX、包信息、签名、zipalign、ABI、native 清单、动态依赖、符号和 16 KB 对齐
7. 真机解码、Anime4K 性能与温度、截图、手势、自动小窗、悬浮/全屏转场、后台行为和站点兼容保持
   `verification_pending`
