---
For_Agent: 对项目大规模动工前按本规范协作
---

# TODO不误砍柴功

## 2026-07-29 浏览器与文件下载器设置页统一

本轮以当前 `KiyoriPlayerSettingsPage` 为唯一设置页视觉和交互基线，把浏览器与文件下载器设置页
统一到同一套分组标题、说明、卡片、双行设置项、Material Switch、禁用态和底部选择面板。继续
复用唯一 `WebSessionBrowserSettingsStore`、`BrowserDownloadSettingsStore` 与
`BrowserDownloadManager`，不创建第二状态 owner、第二下载器或空白功能实现。

共享 UI 契约：

- 页面继续复用 `KiyoriCollapsingSettingsPage` 的大标题折叠头和 `#F5F5F2` 背景
- 分组标题、分组说明、16dp 白色圆角卡片、0.6dp 分隔线以及左右边距完全沿用播放器设置页
- 每个设置项固定为标题与两行内说明，右侧使用紧凑状态摘要、箭头或 Material Switch
- 依赖不满足或尚无真实 owner 的项目使用 42% 透明度和不可点击状态，不再打开空白页面
- 所有单选项共用播放器设置页的 26dp 圆角底部面板，显示标题、当前值、选项说明和蓝色选中标记

浏览器信息架构按使用路径固定为五组：

1. **插件与会话**：网页插件、返回行为和标签恢复
2. **主页、标签与手势**：主页入口、标签样式、导航手势和搜索引擎切换条
3. **音视频嗅探**：搜索栏嗅探入口、自动悬浮播放、悬浮嗅探模式和规则入口
4. **网站权限与数据**：外部应用、位置、翻译、网站配置和密码
5. **显示与高级**：字体、缩放、调试、User-Agent、代理和新窗口

其中主页、搜索栏嗅探入口、自动悬浮播放、允许网页打开应用和允许网页获取位置继续连接现有真实
设置值；其余尚无本页 owner 的项目保留产品信息，但明确显示为未接入状态。浏览器“悬浮嗅探”
面板删除两个设置开关，只负责视频格式筛选、候选列表和播放/下载/复制/查看链接操作；两个开关
只在浏览器设置页出现，避免同一配置存在两个入口。

下载器信息架构按真实执行阶段固定为四组：

1. **下载器与性能**：默认下载器、保存位置、并行任务和普通/M3U8 线程预算
2. **M3U8 与存储**：M3U8 离线包和普通文件读写分块
3. **安装与通知**：安装包清理、下载确认和完成提示
4. **网络协议**：新建内置下载任务使用的 HTTP 协议

“默认保存位置”在同一个选择面板中提供应用下载目录、公开下载目录和 SAF 自定义目录三个明确
选项，底层继续使用既有 `autoTransferToPublicDirectory` 与目录 URI 互斥合同。内置下载器专属
设置在系统下载器生效时显示依赖禁用态；“默认下载器”和“跳过下载确认”保持可操作，内置任务
完成提示则随内置引擎专属设置一起禁用。

实施与验收门禁：

1. [DONE] 提取播放器设置页的分组、设置行和选择面板为共享 Compose 组件，并让播放器页
   无行为变化地复用
2. [DONE] 重构浏览器设置规格、分组说明、动态摘要、真实开关、禁用态和主页子页；把
   “搜索栏嗅探入口”和“自动悬浮播放”从候选面板迁移到“音视频嗅探”分组
3. [DONE] 重构下载器命名、分组说明、状态摘要、依赖禁用态、目录选择和所有选择面板
4. [DONE] 更新 `KiyoriSettingsPagesTest`，覆盖共享尺寸、分组顺序、真实 action 和依赖策略
5. [DONE] 同步 `README.md`、`CONTEXT.md` 与相关浏览器完成度文档
6. [DONE] 执行定向 JVM 测试、Kotlin 编译、formal readiness、`git diff --check` 和最终
   `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
7. [PENDING] 目标设备验收三页滚动折叠、字体与间距、开关热区、底部面板、SAF 目录以及系统/
   内置下载器切换；本轮不安装 APK、不操作设备

本地证据：

- `KiyoriSettingsPagesTest 8/8`、`BrowserDownloadPolicyTest 20/20`、
  `BrowserMediaCandidatePolicyTest 16/16`，合计 `44/44`，零失败、零错误、零跳过
- 主源码 Kotlin 编译通过；formal readiness 和 `git diff --check` 通过，后者只有工作树既有
  CRLF 到 LF 提示，没有 whitespace error
- 静态反向检查确认候选面板中的 `Switch`、“搜索栏嗅探入口”和“自动悬浮播放”均为 `0`，
  旧设置行类型、空白占位 action 和旧下载选择面板常量也均为 `0`
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 3m 54s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 18:58:41 +08:00`，大小 `482590795` 字节，SHA-256
  `432FBD73770E59D408F6AEDD2034E76E7C5FAD0D43150543005F789DE559CB6B`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名和
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK、未操作设备；三页视觉、横屏/竖屏滚动、SAF 选择器和系统/内置下载器切换保持
  `verification_pending`

## 2026-07-29 播放器保存位置、重力旋转与横屏超分布局

本轮继续复用唯一 `PlayerSession`、`PlayerSettingsStore`、
`BrowserDownloadSettingsStore` 和 `BrowserDownloadManager`，不创建第二播放器、第二下载器或平行
目录状态。目标是让播放器截图和右侧下载按钮都具备可解释、可持久化、不会误释放 SAF 权限的保存
策略，同时修复横屏控制层布局和重力旋转冲突。

设置页按用户决策频率和功能关系重排为六组：

1. **播放与连播**：默认倍速、倍速记忆、自动下一集、队列结束行为
2. **手势与进度**：双击、跳转、精确定位、章节、缩略图
3. **画面与超分**：Anime4K 记忆和默认模式、解码器、GPU Next、Vulkan
4. **音频与字幕**：音量增强、字幕缩放
5. **保存与下载**：截图保存位置、视频下载位置
6. **窗口与在线**：跟随重力旋转、退出全屏、后台行为、在线播放缓存

保存位置契约：

- 两个播放器目录默认均为“跟随文件下载器”，不复制或改写下载器主设置
- 跟随模式实时读取文件下载器当前保存策略：自定义 SAF 目录、公开下载目录或应用下载目录
- 截图可单独选择 SAF 目录；播放器直接把 PNG 写入该目录
- 视频可单独选择 SAF 目录；该次请求继续进入唯一 `BrowserDownloadManager`，并冻结独立目录为
  任务目标。Android 系统下载器不能写入任意 SAF 目录，因此独立目录使用现有内置下载引擎
- 目录 URI 与显示名称成对保存；清除独立目录后恢复跟随，不保留隐形目标
- 释放旧 SAF 权限前同时检查下载器主设置、播放器截图设置、播放器视频设置和现有下载任务，避免
  多个功能共用同一目录时误释放权限

旋转和控制层契约：

- “跟随重力自动旋转”关闭时保持现有默认横屏，底部“旋转”按钮可手动切换横竖屏
- 开启后 Activity 使用 `FULL_SENSOR` 跟随设备重力，底部按钮显示“自动”并进入不可点击状态，
  防止手动方向请求与传感器策略互相覆盖
- 横屏 Anime4K 控件使用固定宽度锚定左下角；“超分”和模式名使用紧凑行高，不改变弹窗实时
  切换逻辑

实施与验收门禁：

1. [DONE] 扩展播放器设置模型、目录持久化和跨功能 SAF 权限所有权检查
2. [DONE] 为播放器视频下载请求增加单次目录目标，并贯穿浏览器 host、确认弹窗和下载任务
3. [DONE] 让播放器截图按独立目录或下载器主策略写入真实目标
4. [DONE] 重排设置页、增加两个目录选择项和重力旋转开关，补齐文案与状态摘要
5. [DONE] 修复横屏超分位置和文字间距，协调自动旋转与手动按钮状态
6. [DONE] 更新单元测试和播放器架构文档，执行定向测试、Kotlin 编译、formal readiness、
   `git diff --check` 和最终 Debug APK 构建核验
7. [PENDING] 目标设备验收横竖屏重力切换、横屏超分位置、截图目录、视频下载目录和共享 SAF
   权限；本轮不安装 APK、不操作设备

本地证据：

- `PlayerPolicyTest 22/22`、`BrowserDownloadPolicyTest 20/20`、
  `PlayerControlsPolicyTest 3/3`、`KiyoriSettingsPagesTest 8/8`，合计 `53/53`，零失败、零错误、
  零跳过
- `ci.test.test_player_assets 6/6`、`:app:compileDebugKotlin`、formal readiness 和
  `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，233 个任务零失败；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-29 17:26:26 +08:00`，
  大小 `482590795` 字节，SHA-256
  `723E9A6E9093930CB8249045A91AEAF4DB419CAB3D7A6F1C04C5CC202BEBCEAE`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名通过，
  `zipalign -c -P 16 4` 通过
- 未安装 APK、未操作设备；横竖屏视觉、传感器方向、真实 SAF 写入和在线播放下载仍为
  `verification_pending`

## 2026-07-29 播放器超分、手势、章节预览与连播设置闭环

本轮继续保持唯一 `PlayerSession`、唯一 `:player` MPV runtime、现有 Surface lease 与
`PlayerSettingsStore`。播放器当前尚未发布，因此旧的单一“播放结束行为”会直接清理并替换为
队列语义明确的“自动播放下一集”和“队列播完行为”，不保留并行旧方案。参考
`D:\10_Project\mpv-android-anime4k` 的真实 MPV 功能与
`D:\10_Project\kiyori-android` 的系列识别交互，但不移植第二播放器、第二设置 owner、代理或
伪功能。

信息架构按使用频率和因果关系固定为：

1. **播放与连播**：默认倍速、记忆倍速、自动播放下一集、队列播完行为
2. **手势与进度**：双击手势、双击跳转时长、按钮跳转时长、精确进度定位、章节进度条、
   进度条缩略图预览
3. **画面与超分**：记忆超分模式、默认超分模式、解码器预设、GPU Next、Vulkan
4. **音频与字幕**：音量增强、字幕缩放
5. **在线与窗口**：在线播放缓存、全屏退出行为、切到后台时

实施步骤：

1. [DONE] 扩展 `PlayerSettings` 与持久化：
   - 新增“双击暂停/播放”与“左右双击快退/快进”两种手势模式及独立跳转时长
   - 新增章节节点显示和拖动缩略图预览开关
   - 新增自动播放下一集开关与队列播完后的停留、关闭、循环当前项行为
   - “记忆超分模式”开启后才允许选择默认模式；默认模式仍对应真实 Anime4K shader 文件
2. [DONE] 扩展唯一播放会话：
   - 会话保存真实播放队列、当前索引与前后项可用状态
   - 本地文件按同目录、同系列名和自然序生成队列；在线播放只有调用方明确提供队列时才连播
   - 上一集、下一集和自然播放结束都在同一 runtime 内切换媒体
3. [DONE] 扩展唯一 MPV runtime：
   - 从 `chapter-list` 读取章节标题与起始时间并随文件加载事件返回主进程
   - 使用当前 MPV 绑定的 `grabThumbnailFast` 提取最大 `320px` 的拖动预览帧
   - 缩略图使用单线程、时间分桶和仅保留最新请求的调度，结果按 runtime/load/request generation
     校验，避免旧媒体结果污染当前 UI
4. [DONE] 重建播放器控制交互：
   - 左下角“超分”按钮改为锚定弹窗，列出关闭、流畅、均衡、高清，选择后立即应用
   - 双击手势按设置切换暂停/播放或左右跳转
   - 进度条绘制章节节点，显示当前章节名称；拖动时显示视频画面、时间与章节
   - 上一集/下一集按钮按真实队列状态启用
5. [DONE] 重构播放器设置页：
   - 使用五组标题、说明、现代化卡片与统一的选择弹层
   - 条件项保持可理解的禁用态：未开启记忆超分时默认模式不可操作；双击暂停模式下跳转时长不可操作
   - 每一项只连接 `PlayerSettingsStore` 或真实 MPV/session 能力
6. [DONE] 增加设置映射、系列自然排序、队列结束策略、章节定位、缩略图请求时序与 AIDL
   协议测试，并执行针对性 JVM/Python/Kotlin 检查
7. [DONE] 同步播放器语义与阶段文档，执行 formal readiness、`git diff --check`、
   `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`，核验最终 Debug APK
8. [PENDING] 目标设备验收超分实时切换、两种双击、章节节点/名称、拖动预览、本地系列前后项、
   队列播完行为、在线单项播放、横竖屏设置布局与性能；未操作设备前保持
   `verification_pending`

关键约束与失败检查：

- 缩略图提取不得阻塞 MPV 进度事件线程，不得让快速拖动积压无界请求；Binder 只传递受尺寸约束的
  Bitmap
- 媒体切换、runtime 重启、Surface 转移或退出后，旧章节和旧缩略图结果必须被 generation 丢弃
- `loop-file` 固定关闭，由 `PlayerSession` 处理自然 EOF；否则 MPV 自循环会吞掉“最后一集播完”
  事件
- 本地系列识别失败时只保留当前媒体，不跨目录、不把无关视频加入队列；在线播放不推断网页媒体顺序
- 设置页中的开关、文案、启用态和播放器实际行为必须共享同一设置值，禁止只改 UI

本地验证证据：

- `PlayerPolicyTest`、`PlayerRuntimeProtocolPolicyTest`、`KiyoriSettingsPagesTest` 与
  `PlayerControlsPolicyTest` 合计 `37/37` 通过，零失败、零错误、零跳过
- 播放器 Python 资源与原生依赖门禁 `6/6` 通过；`:app:compileDebugAndroidTestKotlin`、
  formal readiness 和 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 4m 2s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 16:21:32 +08:00`，大小 `474822245` 字节，SHA-256
  `B59594BEAE9F25BDF7F63EB8EA9C6DC7882DE2D7C4B77C8C8454C9464FE1C975`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、仅 `arm64-v8a`；播放器专用
  `libmp*.so` 与 `libplayer.so` 九个条目各存在一次。Android Debug v2 签名与
  `zipalign -c -P 16 -v 4` 验证通过
- 未安装 APK、未操作设备；超分实时切换、缩略图 JNI 性能、章节视觉、队列连播和横竖屏设置布局
  保持 `verification_pending`

## 2026-07-29 播放器控制层触摸、按钮与真实设置收口

用户使用 `2026-07-29 14:31 +08:00` 的 vivo V2507A / Android 16 诊断报告确认：同一
Debug APK 已经能够播放在线 MP4，`runtime=ACTIVE`、`paused=false`、Surface 和首帧均正常。
当前故障已经从在线播放链路收敛为全屏 Compose 控制层问题：本地或在线视频开始播放后，控制层自动
隐藏，单击视频区域无法重新显示。

定向源码审计确认根因与关联缺口：

- `PlayerGestureLayer` 把持续变化的 `state.positionSeconds` 放进两个 `pointerInput` key；播放进度
  更新会取消并重启正在识别的触摸协程，导致播放期间单击、双击和拖动都可能在手指抬起前失效
- 单击与拖动由两个并行 detector 持有，缺少一次手势只能由一个明确模式消费的合同
- 三秒自动隐藏只观察播放/暂停与加载，没有观察弹窗、进度拖动、全屏手势和日志 Dialog；按钮操作也
  不会统一重置计时
- 锁定后手势层被整体禁用，缺少 legacy 的“单击屏幕重新显示解锁按钮”行为
- 顶部和底部弹幕入口仍是活跃空回调；更多菜单除“查看日志”外均为空动作
- `PlayerSettingsStore` 已有精确 seek、网络缓存、字幕缩放、后台行为和结束行为等真实字段，但设置页
  没有完整暴露；悬浮播放器仍把显示用跳转步长固定为 `10`

本轮继续保持唯一 `PlayerSession`、唯一 `:player` MPV runtime、现有 Surface lease、浏览器下载 owner
和已验证的在线 Range/header 修复，不增加第二播放器、第二设置 owner、代理、回退或重载路径。

细化步骤：

1. [DONE] 用单一、稳定且不以播放进度为 key 的 pointer detector 重建单击、双击、水平 seek、
   左侧亮度和右侧音量；每次手势开始时读取最新 session state，播放进度更新不得取消当前触摸
2. [DONE] 建立控制层可见性策略：播放中三秒自动隐藏；暂停、加载、弹窗、日志、进度拖动或全屏
   手势期间不隐藏；任意真实按钮操作重置计时
3. [DONE] 锁定时只保留左右解锁按钮，三秒后隐藏；锁定状态单击视频区域重新显示解锁按钮，解锁后
   恢复完整控制层与自动隐藏计时
4. [DONE] 保留字幕、音轨、画面模式、倍速、播放暂停、前后跳、进度、Anime4K、旋转、截图、锁定、
   浏览器下载和日志的真实命令；弹幕在没有真实 owner 前显示为明确禁用态，更多菜单删除活跃空动作
5. [DONE] 播放器设置页删除无 owner 的静态伪设置，只展示并接通
   `PlayerSettingsStore` 的默认/记忆倍速、跳转步长、精确 seek、后台行为、全屏退出、结束行为、网络
   缓存、字幕缩放、解码器、GPU Next、Vulkan、记忆 Anime4K 与音量增强；初始化期设置明确标注下次
   启动播放器生效
6. [DONE] 悬浮播放器读取同一设置 owner 的跳转步长，避免 UI 文案和 `PlayerSession` 实际命令不一致
7. [DONE] 增加控制层策略、设置能力映射和静态触摸门禁测试，执行定向 JVM/Python 检查、Kotlin
   编译、formal readiness、`git diff --check` 与串行 Debug APK 构建核验
8. [PENDING] 目标设备验收本地/在线视频的隐藏后单击恢复、双击、滑动、按钮热区、弹窗计时、锁定、
   横竖屏与设置实时/下次启动生效语义

本地证据：

- 播放器与设置定向 JVM 回归 `58/58`，零失败、零错误、零跳过；播放器 Python 静态资源与触摸门禁
  `6/6` 通过
- `:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin`、formal readiness 与
  `git diff --check` 通过；差异检查只有工作树既有的 CRLF -> LF 提示，没有 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 49s`，
  233 个任务零失败，末尾 `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 15:22:40 +08:00`，大小 `474822245` 字节，SHA-256
  `96726F63381FA7AEDF4AE212E162D3999BF057C2A3AB9EE1C0B6CA95B578788F`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、`arm64-v8a`，Android Debug v2 签名与
  `zipalign -c -P 16 -v 4` 验证通过
- 未安装 APK、未运行设备或模拟器；本轮真机触摸、视觉和设置生效验收保持
  `verification_pending`

## 2026-07-29 播放器原生 HTTP/HTTPS 在线播放修复

### 第二份真机报告纠正与固定 Range 修复计划

用户安装上一版 APK 后，于 `2026-07-29 13:53 +08:00` 再次导出 vivo V2507A / Android 16
播放器报告。新证据证明上一轮只修复了 HTTPS 协议装载，不能表述为在线播放已经修复：

- `mpv 0.41`、FFmpeg `n8.1.2`、Mbed TLS、严格证书校验和固定 CA 包均已加载；
  `https://` 已进入 FFmpeg，Surface、GLES、VO 与 `:player` 进程保持正常
- 实际失败变为
  `Unexpected offset: expected 0, got 19890176`
- 报告中的公开视频当前总长为 `25260223` 字节；同一 URL 使用
  `Range: bytes=19890176-` 会返回
  `Content-Range: bytes 19890176-25260222/25260223`
- FFmpeg `n8.1.2` 的 HTTP 实现会先以内部 `off` 生成自己的 Range；若自定义 headers 已含
  `Range`，则不生成内部 Range，但响应 `Content-Range` 仍会更新实际偏移，最终在期望偏移与
  实际偏移不一致时显式失败
- 当前 `BrowserMediaCandidate` 会保存浏览器某一次网络请求的 `Range`，播放器又把候选的全部
  headers 固定写入 `http-header-fields`，因此浏览器分段位置错误地成为后续 mpv 探测、读取和 seek
  的静态请求头
- `mpv_event_to_node` 把 `END_FILE.reason` 输出为字符串 `error`，错误文本位于
  `file_error`；当前 engine 按整数 reason 和整数 `error` 读取，导致报告误写
  `reason=unknown error=none`

本轮修复边界：

1. [DONE] 以第二份真机报告、公网响应和 FFmpeg/mpv 源码确认固定 Range 是当前唯一可复现根因
2. [DONE] 在 MPV 网络请求边界建立纯策略：保留 URL、User-Agent、Referer、Origin、
   Cookie、Accept 及其他已观察请求头，但不把浏览器捕获的 `Range` 固定交给 mpv；Range 继续保存在
   candidate 中供下载和诊断使用，播放器传输偏移只由 mpv/FFmpeg 当前状态持有
3. [DONE] 日志只记录输入/转发 header 数量、header 名称和 `Range` 由 MPV 管理的决策，
   不记录任何 header 值
4. [DONE] 按 `mpv_event_to_node` 的真实 schema 读取 `END_FILE.reason` 与 `file_error`，
   确保加载失败进入 ERROR 日志和可见播放器错误
5. [DONE] 补充大小写不敏感的 Range 排除、其他请求头原样保留、END_FILE schema 和日志隐私测试，
   同步修正文档中“把 Range 原样交给 mpv”的错误表述
6. [DONE] 执行定向 JVM/Python 检查、formal readiness、差异反向审查和串行 Debug APK 构建
7. [PENDING] 目标设备复测本次 MP4、普通 HTTPS MP4、HLS、带 Referer/Cookie 的链接、首次加载和
   seek；真机成功前任务状态保持 `verification_pending`

当前定向证据：

- `PlayerPolicyTest` `17/17`、`PlayerRuntimeProtocolPolicyTest` `6/6`、
  `BrowserMediaCandidatePolicyTest` `16/16` 通过，合计 `39/39`，零失败、零错误、零跳过
- 播放器 Python 原生依赖与资源门禁 `20/20` 通过，AndroidTest Kotlin 编译和
  formal readiness 通过
- 主机 FFmpeg 在无自定义 Range 时成功读取该公开视频首帧；固定
  `Range: bytes=19890176-` 时立即拒绝输入，验证修复前后的协议差异
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 4m 18s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 14:22:10 +08:00`，大小 `474822245` 字节，SHA-256
  `969C20C2FC6E1A401CFF51812EC1936AB674ECF5B2F51D0A7AB1589FBB35A6A7`
- APK 为 `com.kiyori`、`45 / 0.1.0`、仅 `arm64-v8a`；53 个 native entry 中播放器
  10 个目标库各存在一次。Android Debug v2 签名与 `zipalign -c -P 16 -v 4` 通过
- 未安装 APK、未操作设备；目标设备在线播放仍为 `verification_pending`

用户于 `2026-07-29 13:06 +08:00` 导出的 vivo V2507A / Android 16 播放器诊断报告确认：
浏览器已把直接 HTTPS MP4 及八个请求头交给唯一 `PlayerSession`，`:player` 进程中的 Surface、GLES、
VO 和渲染尺寸均已正常建立，但 `libavformat.so` 返回 `No protocol handler found to open URL`。
二进制审计进一步确认，当前打包给 `libmpv.so` 的 FFmpegKit `n8.1.2` 明确禁用了 OpenSSL 且未启用
其他 TLS 后端；固定 mpv 输入自带的同版本 FFmpeg 则启用了静态 Mbed TLS，并且七个 ELF 均满足
`PT_LOAD >= 0x4000`。

本轮保持唯一 `PlayerSession`、唯一 `:player` MPV runtime、现有 Surface lease 和浏览器请求上下文。
主进程的 FFmpegKit 仍服务 FFmpeg 工具箱、媒体处理与浏览器下载合并；播放器使用的上游 FFmpeg
通过等长 SONAME / `DT_NEEDED` 命名空间隔离，禁止同名覆盖、Gradle `pickFirst`、应用层代理、
第二播放器或 URL 回退路径。

细化步骤：

1. [DONE] 审计诊断报告、两个固定输入 AAR、FFmpeg 编译配置、动态依赖、协议文本与 16 KB 对齐
2. [DONE] 扩展确定性 mpv AAR 转换：保留并等长改名上游七个 FFmpeg ELF，重写其
   SONAME / `DT_NEEDED`，让 `libmpv.so` 只解析隔离且启用 Mbed TLS 的 native 闭包
3. [DONE] 增强输入和 APK 门禁：核对命名空间互斥、旧依赖名清零、Mbed TLS / HTTPS 编译证据、
   唯一 C++ runtime、精确 native 清单和 16 KB ELF
4. [DONE] 初始化时复制固定 `cacert.pem` 并设置 `tls-ca-file`，保持 `tls-verify=yes`；
   禁用未随 APK 分发的 ytdl hook，直接媒体 URL 不再调用缺失的外部脚本
5. [DONE] 补充依赖转换、engine 选项和在线播放错误路径测试，更新 native 栈、构建、NOTICE、
   `CONTEXT.md` 与阶段 8/10/11 的权威说明
6. [DONE] 执行定向 Python/JVM 测试、Kotlin 编译、formal readiness、差异与隐私反向检查，
   最终串行构建并核验 Debug APK
7. [PENDING] 在目标设备复测 HTTPS MP4、HLS、带 Referer/Cookie 的链接、证书失败、重定向、
   网速/缓冲状态及日志复制导出

本地证据：

- 依赖转换测试 `14/14`、播放器资源门禁 `6/6`、`PlayerPolicyTest` 与
  `PlayerRuntimeProtocolPolicyTest` `20/20` 通过，零失败、零错误、零跳过
- `:app:verifyPlayerNativeInputs`、`:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin`、
  formal readiness 和串行 `:app:assembleDebug` 通过；最终构建为 `BUILD SUCCESSFUL in 4m 18s`，
  233 个任务零失败，末尾 `:app:verifyDebugPlayerRuntimePackaging` 通过
- mpv AAR 为 `50543589` 字节、SHA-256
  `FC983B7ED0C8B8BE1938283FE94108DFDC593AA31608D55DD1CE119AE201C32C`
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 14:22:10 +08:00`，大小 `474822245` 字节，SHA-256
  `969C20C2FC6E1A401CFF51812EC1936AB674ECF5B2F51D0A7AB1589FBB35A6A7`
- APK 仅 `arm64-v8a`；53 个 native basename 无重复，52 个 ELF 全部
  `PT_LOAD >= 0x4000`，唯一非 ELF 为既有 2 字节 `libsudo.so`
- `libmpv.so` / `libplayer.so` 对正常 `libav*.so` 的 `DT_NEEDED` 为零；256 个版本化 FFmpeg
  符号去重后由 `libmp*.so` 全部提供，缺失 0；99 个唯一 C++ 引用缺失 0
- `libmpformat.so` 包含 FFmpeg `n8.1.2`、`--enable-mbedtls`、Mbed TLS 3.6.6、
  `mbedtls_ssl_handshake` 和 HTTPS 证据；Android Debug v2 与 16 KB ZIP 对齐通过
- 未安装 APK、未运行设备或模拟器；真实在线播放仍为 `verification_pending`

## 2026-07-29 播放器日志弹窗真机反馈修正

用户在 `2026-07-29 12:39 +08:00` 的横屏真机截图中确认：顶部状态两行已经完整显示，但上行使用
粗体、下行使用常规字重；日志弹窗的等权筛选项发生换行，固定 `360dp` 日志区又把复制、导出、清空和
关闭操作挤出屏幕。本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为播放器唯一进度载体，不改变 `PlayerSession`、跨进程日志协议、脱敏规则或导出目录。

细化步骤：

1. [DONE] 统一网速/单位和电量/时间上下两行的 `fontSize`、`lineHeight` 与 `fontWeight`
2. [DONE] 将日志弹窗改为屏幕内固定比例布局，标题、分类、正文和操作区不再相互挤压
3. [DONE] 把分类改为可横向滑动且永不换行的单行选项条，覆盖全部、错误、警告及错误、
   网络与加载、播放控制、画面与 Surface、音轨与字幕、运行时
4. [DONE] 日志缓冲提供结构化条目和变更 revision；查看区使用最新在前的懒加载列表并实时更新，
   复制和导出继续生成时间正序的完整诊断报告
5. [DONE] 顶部固定关闭入口，底部固定清空、复制日志和导出文件；清空使用二次确认，
   导出进行中禁止重复触发
6. [DONE] 更新播放器语义文档和阶段 11，执行定向 JVM、播放器 Python 门禁、Kotlin 编译、
   formal readiness、`git diff --check` 与 Debug APK 构建核验
7. [PENDING] 目标设备上的横竖屏尺寸、分类横滑、实时更新、底部按钮可见性、复制和导出验收

本地证据：

- `PlayerPolicyTest` 与 `PlayerRuntimeProtocolPolicyTest` 共 `20/20` 通过，零失败、零错误、零跳过；
  播放器 Python 资源门禁 `6/6` 通过
- `:app:compileDebugKotlin` 与 `:app:compileDebugAndroidTestKotlin` 通过；formal readiness、
  隐私反向扫描和 `git diff --check` 通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 17s`，233 个任务零失败，构建末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-29 13:00:54 +08:00`，
  大小 `463725699` 字节，SHA-256
  `652ECA3B6A6DED8EEB16B77AF72C2F28BE6BC49C81701B0FDE3D13DE12C6C17E`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名与
  `zipalign -c -P 16 4` 验证通过
- 未安装 APK、未运行设备或模拟器；本轮真机视觉与操作验收保持 `verification_pending`

## 2026-07-29 播放器顶部状态与诊断日志增强

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为播放器唯一进度载体，不创建第二套播放器、mpv core、网络请求或日志状态。目标是在保持顶部按钮
外部尺寸和位置不变的前提下修复全屏控制层，并让“查看日志”成为在线播放问题可直接导出的诊断入口。

细化步骤：

1. [DONE] 保持字幕、弹幕、音轨和画面模式四个按钮的外部尺寸与布局权重不变，只缩小内部
   padding，让描边图标从 `20dp` 增大到 `24dp`
2. [DONE] 删除全屏顶部状态列的固定裁切高度，显式设置两行文字的 `lineHeight`、单行和禁用换行，
   保证横屏、竖屏及系统字体缩放下显示网速单位和电量下方时间
3. [DONE] 把独立 `:player` 进程中的 runtime 命令、mpv 初始化、Surface、加载、文件事件和
   `MPVLib.LogObserver` verbose 日志通过现有 AIDL callback 汇入唯一 `PlayerDebugLogBuffer`
4. [DONE] 对诊断内容统一限制行数和单行长度，脱敏 Cookie、Authorization、请求头、URL 查询值及
   私人路径；在线播放保留协议、host、端口和路径结构用于定位
5. [DONE] 让查看日志提供“全部 / 警告+错误 / 仅错误”三级过滤；界面、复制与导出读取同一份
   当前过滤报告，导出文本写入 `Download/Kiyori/exports`
6. [DONE] 更新播放器语义文档和阶段 11，执行定向 JVM、播放器 Python 门禁、Kotlin 编译、
   formal readiness、`git diff --check` 与 Debug APK 构建核验
7. [PENDING] 目标设备上的横竖屏双行状态、图标视觉、在线播放错误日志完整性、复制和导出路径验收

本地证据：

- `PlayerPolicyTest` 与 `PlayerRuntimeProtocolPolicyTest` 共 `18/18` 通过，零失败、零错误、零跳过；
  播放器 Python 资源门禁 `6/6` 通过
- `:app:compileDebugKotlin` 与 `:app:compileDebugAndroidTestKotlin` 通过；formal readiness 和
  `git diff --check` 通过
- `:app:assembleDebug` 通过，233 个任务零失败，构建末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-29 12:27:11 +08:00`，
  大小 `463725699` 字节，SHA-256
  `FDF25649812F22F2C4406D5334E5D84DC4117933B165A911BA4EE9DA6662E50F`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名与
  `zipalign -c -P 16 4` 验证通过
- 未安装 APK、未运行设备或模拟器；横竖屏视觉、在线播放日志完整性、复制和导出仍为
  `verification_pending`

## 2026-07-28 播放器进程崩溃隔离与诊断页规划

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为播放器唯一进度载体，不创建第二套 PlayerSession、mpv core 或崩溃页面。当前播放器、悬浮嗅探
和浏览器位于同一主进程；native fatal signal 会让 Browser Runtime 与播放器一起退出，现有
`GlobalExceptionHandler` 无法在进程已经死亡后启动 `:crash` 页面。

规划采用三个明确进程边界：

1. 主进程保留唯一 `PlayerSession`、Browser Runtime、PlayerActivity 和 Surface lease
2. 非导出的 `:player` 绑定服务只持有唯一 `MpvPlayerEngine`、媒体描述符和串行 MPV 调用线程
3. 现有 `:crash` 继续展示结构化报告，不在该进程创建 `PlayerSession`

后续实施分为结构化报告、AIDL runtime、Binder death 诊断闭环、清理与设备验收四个串行里程碑。
Surface attach/detach 必须等待带 generation 的 remote ACK；runtime 死亡不会自动 bind 或 load，
用户只能通过崩溃页明确请求重新启动播放器。报告持久化禁止保存 headers、Cookie、完整 URL 和私人路径。

完整设计、影响文件、风险、自动测试和真机步骤见
[`12_player_process_crash_isolation.md`](kiyori_browser_product_completion/12_player_process_crash_isolation.md)。
当前本地状态为 `[LOCAL DONE]`。里程碑 12.1、12.2 与 12.3 已完成：结构化崩溃报告、跨进程原子存储、
双模式 `CrashReportActivity`、唯一非导出 `:player` AIDL runtime、串行 MPV owner、事件推送和
Surface 两阶段 ACK、Binder death、退出证据、前后台报告展示和用户明确重启已接通。自动检查与
各里程碑 Debug APK 构建均通过。设备安装和崩溃注入未获授权，最终保持
`verification_pending`。

## 2026-07-28 浏览器视频嗅探抽屉优化

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器视频候选与播放器入口的唯一进度载体，不创建第二套 WebSession、播放器或下载状态。
播放器当前无法正常播放的问题不属于本轮范围；本轮只保证视频候选入口把原始请求交给现有
`PlayerSession`，并让手动播放直接进入既有横向全屏 `PlayerActivity`。

细化步骤：

1. [DONE] 拆分“搜索栏嗅探入口”和“自动悬浮播放”两个设置，删除旧的混合职责字段
2. [DONE] 为候选增加精确视频格式、被动时长、页面播放器状态与确定性推荐排序
3. [DONE] 只向浏览器 UI 投影可执行视频，排除音频、blob/MSE、MIME-only API 和媒体分片
4. [DONE] 重做视频资源抽屉：顶部双开关、横向动态格式选框、链接与时长、推荐标记和紧凑操作按钮
5. [DONE] 点击或长按结果打开“播放资源 / 下载资源 / 复制链接 / 查看链接”居中弹窗
6. [DONE] 手动播放固定进入横向全屏，自动悬浮播放继续使用同一 `PlayerSession`
7. [DONE] 更新语义文档，执行定向测试、formal readiness、`git diff --check` 与 Debug APK 构建核验
8. [PENDING] 目标设备上的字体、间距、横向筛选、点击/长按、开关独立性和横屏启动验收

本地证据：

- `BrowserMediaCandidatePolicyTest` 与 `KiyoriSettingsPagesTest` 共 `24/24` 通过，零失败、零错误、零跳过
- `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- `git diff --check` 通过；只报告工作树既有 CRLF 到 LF 提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 通过，含
  `verifyDebugPlayerRuntimePackaging`
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`463722319` 字节，SHA-256
  `5BA9E3AC56B7133DB44D8C08AC4257CEA221F393C7164DB4B6640A30B706ED21`
- APK 为 `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、min 26/target 34，
  Android Debug V2 签名与 16 KB ZIP 对齐通过
- 本轮权威日期是 `2026-07-28`；构建环境文件时间记录为未来的
  `2026-07-29 02:40:30 +08:00`，仅作为本机时钟元数据，不改变任务日期
- 未安装 APK、未运行设备或模拟器；播放器仍无法实际播放视频，字体/间距、操作弹窗和横屏入口保持
  `verification_pending`

## 2026-07-28 浏览器悬浮球长按关闭交互

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器 presentation 的唯一进度载体，不创建第二套悬浮窗、浏览器状态或关闭流程。悬浮球关闭
动作必须与浏览器下拉抽屉第 4 行第 1 个“退出浏览器”按钮共用 `onExitBrowser`。

细化步骤：

1. [DONE] 单击悬浮球继续打开现有 Browser Home
2. [DONE] 长按悬浮球时消费本次进入动作，并在球体右上角外围显示透明背景的红色关闭叉号
3. [DONE] 长按松手后保留叉号 3 秒，超时自动隐藏
4. [DONE] 点击叉号调用既有 `onExitBrowser`，不复制 Browser Runtime 清理逻辑
5. [DONE] 补充纯逻辑状态测试，更新语义文档并执行 formal readiness、差异检查与 Debug APK 构建
6. [PENDING] 目标设备上的长按阈值、松手事件、叉号触控区域、拖动冲突和关闭结果验收

本地证据：

- indicator 状态、外围窗口 flags / 几何、background anchor 与 presentation release 定向 JVM 测试 `16/16`
- Kotlin 编译、formal readiness、`git diff --check` 与 `:app:assembleDebug` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`463722319` 字节，SHA-256
  `689A7EA123EC0107C7A29E8A82138BE6B383374EBC1365325A3F1816D6BFD463`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug V2 签名与 16 KB ZIP 对齐通过

### 右上角外围迭代

本轮在现有长按状态策略上继续迭代，不扩大常驻 `40dp` indicator 窗口，也不把透明触控区留在
其他应用上方。

1. [DONE] 删除球体内部叉号，把关闭动作改为同一 host 管理的透明 `28dp` 独立临时 overlay，
   只绘制 `16dp` 红色叉号
2. [DONE] 长按期间显示叉号但设置 `FLAG_NOT_TOUCHABLE`，松手后才允许点击
3. [DONE] 按球体右上角外围计算位置，拖动时同步移动，并约束叉号窗口不超出屏幕
4. [DONE] 隐藏 indicator、打开浏览器、下载确认、超时和关闭时统一移除临时 overlay
5. [DONE] 补充几何、flags 和状态测试，更新语义文档并重新生成核验 Debug APK
6. [PENDING] 目标设备上的外围视觉、长按不中断、松手可点击、屏幕边缘和拖动同步验收

## 2026-07-28 全屏搜索顶栏与三行输入细化

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为全屏搜索与浏览器顶栏的唯一进度载体，不创建平行 TODO 目录。实现继续复用同一个
`WebSessionBrowserSearchScreen`、Browser Runtime、搜索引擎 owner 和 Profile owner。

细化步骤：

1. [DONE] 缩小搜索历史垃圾桶，并锁定标题行高度，避免进入编辑态时标题上下移动
2. [DONE] 把复制链接、编辑链接图标稍微下移，使图标与下方文字更紧密
3. [DONE] 让全屏搜索页直接复用浏览器顶栏的左右边距、三槽间距、动作区和搜索框宽度
4. [DONE] 搜索框保持固定宽度和顶部位置，输入在 `1..3` 行内自动换行并平滑向下增高
5. [DONE] 超过三行后只允许输入内容纵向滚动，左右按钮和内部引擎按钮始终垂直居中
6. [DONE] 浏览器顶栏右侧固定为刷新动作，不切换叉号，并使用与无痕按钮一致的圆形点击反馈
7. [DONE] 更新语义文档，执行定向测试、formal readiness、差异检查与 Debug APK 构建核验
8. [PENDING] 目标设备上的多行输入、输入法、动画、触控反馈、旋转与浏览器刷新验收

## 2026-07-28 浏览器搜索引擎切换条与外部跳转提示清理

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器产品能力的唯一进度载体，不创建平行 TODO 目录。搜索引擎切换条严格参考
`D:\10_Project\kiyori-android` 的既有交互，并接入当前唯一 Browser Runtime。

细化步骤：

1. [DONE] 在浏览器顶栏下方增加可横向滑动的搜索引擎切换条，只在用户提交文本搜索后显示
2. [DONE] 点击引擎后使用同一搜索文本重新解析并跳转，当前引擎同步写入既有搜索设置 owner
3. [DONE] 提供右侧关闭按钮，并在地址导航、空输入或用户关闭后隐藏切换条
4. [DONE] 删除 `pendingExternalOpenRequest` 一次性确认状态及 Browser Home、悬浮提示中的对应 UI
5. [DONE] 保留“允许网页打开应用”作为唯一权限 owner：启用时仅显式主框架手势执行外部 Intent
6. [DONE] 把浏览器菜单第 4 行退出与设置按钮向内移动，收起按钮保持水平居中
7. [DONE] 更新浏览器语义文档，执行定向测试、formal readiness、差异检查与 Debug APK 构建核验
8. [PENDING] 目标设备上的引擎切换、外部 scheme、菜单位置与触控反馈验收

## 2026-07-28 全屏搜索页现代化完善

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器产品能力的唯一进度载体，不创建平行 TODO 目录。目标是参考
`D:\03_Default\图片\Kiyori\全屏搜索页` 完善共享全屏搜索页；Kiyori 尚未发布，旧的搜索页布局可直接
替换，不保留兼容开关或回退路径。

细化步骤：

1. [DONE] 接入九个真实搜索引擎图标，搜索框只显示图标与箭头
2. [DONE] 把引擎选择器改成淡蓝背景的覆盖式浮层，选项使用白底黑字和图标
3. [DONE] 把当前网页改成标题/网址双行，并保留复制链接与编辑链接两个纵向图文动作
4. [DONE] 把搜索历史改成自适应标签；垃圾桶进入删除模式，标签删除暂存到“完成”后提交
5. [DONE] 按浏览器下拉抽屉菜单基准大幅缩小全部图标、字体、卡片与上下间距
6. [DONE] 点击引擎面板周围收起；点击当前网页标题/网址区域返回现有网页
7. [DONE] 缩小历史标题与文字、放大垃圾桶；清空改为底部确认后立即执行，标签叉号仍由“完成”提交
8. [PENDING] 真机视觉、键盘、旋转、触控反馈和无痕 Profile 交互验收

实现与证据详见
[`2_software_home_and_fullscreen_search.md`](kiyori_browser_product_completion/2_software_home_and_fullscreen_search.md)。

## 无组织无纪律是谓乌合

如果你想做一个消费一定时间、有一定规模的改动：
- 接下的issue可能被别人捷足先登，努力只能存档吃灰
- 一个承诺就此忘却，对应的issue高高挂起
- 一合并激起千重浪，矛盾...冲突！

## 优雅的协作始于你知道我知道你知道

那么，起一个TODO吧：
- 创建一个以你的修改特性命名的文件夹
- 写下index.md，在元数据中填入您的fork仓库地址
- 原本状况是什么样的？你的大致意图是什么？你期待什么样的结果？
- 再写下你的大致作用域，PR，然后干活吧

为什么不是issue: 
- 在BugReport和featureRequest里面捞协作者，是一种奢求
- Agent大概率不会看issue，但绝对不会不瞪一眼文档

## Step By Step

- 按照顺序创建一些以数字+步骤意图命名的MarkDown文档
- 添加每个功能元的旧实现情况，意图修正和期待的新实现情况
- 如果你写下细化的作用域，我们就能更快跟进
- 每一个文档分拆出一个最小可用功能单位，完成你的代码的时候在结尾加一个[DONE]，一起传上去吧
- 别人就可以让Agent根据该文档的git历史捞出对应的differ，没有压缩的话
- 完成最后一个更改时，你可以上传您的详细文档、实验记录、稳定API，并执行自动化i18n
- squansh时尽量不要把i18n和文档更改放进一个pr，这会导致git历史不那么整洁
- 用单独的pr将您的文件夹移入 `docs/.Meta/Legacy/TODO`

## 如果你想鸽了

在你的命名文件夹前加上give-up_，我们的收尾人会迅速继承您的衣钵
如果你忘了这回事，我们的赛博监工会时不时看看你的仓库有没有动静

## 实在懒得写呀...

事实上哪怕您使用Agent完成代码，必要的计划也能使得使得准确率更高，咋一看本计划会留下大量不可压缩的历史更改，但是事实上因为高度可读，不会是一个仓库的负担。并且大型修改在无充分文档和测试记录条件下快速上线，一旦出现问题，新增修改反而会产生更多不可合并的脏历史。

当然，如果您是纯粹新增或修改少量代码，那么的确可以快速上线，本文档是给那些雄心勃勃，致力于大型计划的潜在贡献者们的
以及遇事不决开个goal的Agent

## 2026-07-28 浏览器 presentation 与播放器 Surface lease 正式实现

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为唯一项目进度载体，不创建平行计划目录。Kiyori 尚未发布，完整系统 overlay 浏览器方案在新架构完成后直接删除。

实施严格分为三个串行里程碑：

1. [DONE] 浏览器 presentation
   - `MainActivity`、Kiyori App Shell 和 Browser Home 成为唯一完整浏览器 UI
   - `WindowManager` 只保留同一活动 WebView 的 1×1 后台 attach anchor、最小 indicator 和简短提示
   - indicator 通过显式 action 打开现有 Browser Home
   - 人工 UI 与 AI `browser_*` 继续共用同一 session registry、`activeSessionId` 和 WebView
2. [DONE] 播放器 Surface lease
   - `PlayerSession` 增加 role、owner token、单调 generation、transfer phase、pending target 和一次性 Activity 请求
   - floating 与 fullscreen 必须先确认旧 Surface 销毁和 native detach，再连接新 Surface
   - `MpvPlayerEngine.attachSurface()` 不再隐式 detach，转挂期间不执行媒体重载
3. [DONE] 旧路径与文档收口
   - 删除 expanded overlay 的窗口、Back、cutout、IME、恢复状态和测试
   - 更新 `CONTEXT.md`、`README.md`、正式架构文档及浏览器阶段 1、阶段 11 和总 index
   - 执行静态反向检查、定向测试、Kotlin 编译、正式开发门禁、`git diff --check` 和最终 Debug APK 审计

每个里程碑必须完成独立 Debug 构建并核验 APK 后才能进入下一阶段。真机安装、ADB、MuMu、Release、提交和推送均不属于本轮授权。

当前证据：

- 里程碑 1：浏览器定向 JVM `46/46`，Kotlin 编译、formal readiness、`git diff --check`、Debug 构建和 APK 审计通过；APK SHA-256 `84D7067F361FB9A582DE649DC787B50849AC5D122B6813D2264CF6704F3C7739`
- 里程碑 2：播放器定向 JVM `20/20`，Kotlin 编译、formal readiness、`git diff --check`、Debug 构建和 `verifyDebugPlayerRuntimePackaging` 通过；APK SHA-256 `08A443EAB2ED4FE1F2ACE99E5107AF791EB064EF0C82D53EAA2C413855764BAE`
- 里程碑 3：浏览器与播放器综合 JVM `66/66`，Kotlin 编译、formal readiness、静态反向检查、`git diff --check`、Debug 构建和 `verifyDebugPlayerRuntimePackaging` 通过；最终 APK SHA-256 `689A09C1DF4E23608A4E9E07A7B0E95645F9D5914E3CFB29BA0DFD1A34C2DBA0`

三个本地里程碑均已完成。vivo Android 16 同一网页、同一视频的人工/AI 共用浏览器与 floating/fullscreen 往返仍未执行，因此最终交付状态保持 `verification_pending`。

## 2026-07-28 浏览器 presentation 重复释放与跨窗口交接修复

首轮真机验收发现两条新的 presentation 生命周期故障：

- Browser Home 的显式退出已经完成 `APP_SHELL -> BACKGROUND_ANCHOR`，随后
  `DisposableEffect.onDispose` 再次释放同一 presentation；第二次后台 anchor 请求把已经挂在
  1×1 anchor 的 WebView 当成未挂载对象，触发 `Active WebView must be detached` 断言
- vivo Android 16 在同一时间窗口出现 RenderThread `fdsan` SIGABRT；现场 native 栈只能确认
  图形 Surface 分配路径崩溃，不能仅凭本地代码宣称根治

本轮修复门禁：

1. [DONE] presentation lease 与 host 两层重复 release 都必须幂等
2. [DONE] `APP_SHELL` 与 `BACKGROUND_ANCHOR` 跨 ViewRoot 转移必须先 detach、确认
   `parent == null`，跨过一个渲染帧后才创建或连接目标窗口
3. [DONE] Browser Home 活跃时不保留空的 background anchor 窗口；直接打开 Browser Home
   的入口不得先制造一次无意义的后台挂载
4. [DONE] 定向 JVM、Kotlin 编译、formal readiness、`git diff --check` 与 Debug APK 构建通过
5. [PENDING] vivo Android 16 使用同一网页复测退出、AI 往返、indicator 恢复和悬浮播放；设备
   通过前保持 `verification_pending`

本地证据：

- presentation release gate、background anchor policy 及关联浏览器状态测试合计 `51/51`，零失败、
  零错误、零跳过；测试任务包含 `:app:compileDebugKotlin`
- 静态反向检查确认旧预热入口 `0`、coordinator 提前创建 anchor `0`、严格
  `parent == null` 断言 `1`、显式跨帧 transfer plan 引用 `14`
- formal readiness 与 `git diff --check` 均通过
- `:app:assembleDebug --no-daemon --console=plain`：PASS，233 个任务，零失败；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-07-28 19:01:32 +08:00`，
  `455956807` 字节，SHA-256
  `8DD13C5D3AA81AEAF3102A341FAF485233FF50F1D14BC690C5A608FA3C96ABA6`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、Android Debug v2；
  `zipalign -c -P 16 -v 4` 为 `Verification successful`

Java `IllegalStateException` 的重复释放路径已在状态机与调用层闭环。native `fdsan` tombstone
没有 Kiyori 或 mpv 业务帧，当前实现仅能确认已移除同一时机重复跨 ViewRoot 转挂及空 anchor
预热；目标设备复测前不宣称 native 崩溃已经根治。

## 2026-07-28 播放任意视频即闪退修复

`2026-07-28 19:18:21 +08:00` 的 vivo Android 16 tombstone 显示应用启动 12 秒后在首次视频
渲染期间再次触发 RenderThread `fdsan`。这次没有 Browser presentation 退出前提，必须重新检查
所有播放入口共用的 mpv 启动时序。

本轮门禁：

1. [DONE] 恢复实际打包 mpvlibAndroid 的生命周期：先 `MPVLib.create` 和
   `MPVLib.init`，只在有效 Surface 到达后执行 `attachSurface`
2. [DONE] 保留唯一 `PlayerSession` 和 Surface lease；媒体 request 可先解析，但
   `loadfile` 必须等待当前 lease 完成 native attach
3. [DONE] 增加自动检查，禁止再次在 `MpvPlayerEngine.initialize` 中 attach Surface 或设置
   `force-window=yes`
4. [DONE] 执行播放器定向测试、Kotlin 编译、formal readiness、`git diff --check` 和最终
   Debug APK 构建审计
5. [PENDING] 在原 vivo Android 16 上复测任意网络视频、浏览器悬浮播放和全屏播放；设备通过前
   保持 `verification_pending`

本地证据：

- `ci.test.test_player_assets`：`6/6`，新增启动顺序门禁通过
- `PlayerPolicyTest 12/12` 与 `PlayerSurfaceLeasePolicyTest 8/8`：合计 `20/20`，零失败、
  零错误、零跳过；任务包含 `:app:compileDebugKotlin`
- 静态反向检查：`initialize` 内 `MPVLib.attachSurface=0`、初始化前
  `force-window=yes=0`、`MPVLib.init=1`
- formal readiness 与 `git diff --check` 均通过
- `:app:assembleDebug --no-daemon --console=plain`：PASS，233 个任务中
  27 executed / 206 up-to-date；`verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-07-28 19:39:17 +08:00`，
  `455956807` 字节，SHA-256
  `91634B63C7D5D6EDC5FF20FBBE76663F7850D8A446D52874B556353E1174FB9E`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、Android Debug v2，16 KB ZIP
  对齐通过

## 2026-07-28 hikerView 视频播放器设置页复刻与渲染配置扩展

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为唯一播放器进度载体，不创建平行 TODO 目录。参考图来自
`D:\10_Project\hikerView`；截图定义页面顺序、文案、分组和默认勾选状态，hikerView 源码用于核对
小窗、直接全屏、播放进度、重力感应和 M3U8 等行为语义。

实施门禁：

1. [DONE] 按截图完整呈现 `4/5/8/4/3/1` 六组共 25 个原始选项，保持原顺序、右侧摘要和
   勾选状态
2. [DONE] 追加解码器预设、GPU Next 渲染、Vulkan 渲染上下文、记忆超分模式、记忆播放倍速和
   音量增强六个选项
3. [DONE] 解码器预设复用 mpv 内置 `fast/default/high-quality/gpu-hq/low-latency/sw-fast`
   profile；GPU Next 与 Vulkan 只由唯一 mpv engine 在内核创建时消费
4. [DONE] 记忆超分、记忆倍速和音量增强由唯一 `PlayerSettingsStore` 持久化，并由唯一
   `PlayerSession` 或 `MpvPlayerEngine` 真实消费
5. [DONE] 当前 Kiyori 没有真实 owner 的 hikerView 原始选项沿用浏览器设置页既有契约，只显示
   静态状态或空动作，不新增第二播放器、第二设置 owner 或伪造成功
6. [DONE] 更新 `CONTEXT.md`、`README.md` 和播放器阶段文档，执行定向测试、Kotlin 编译、
   formal readiness、`git diff --check` 与最终 Debug APK 构建和产物核验

真机安装、ADB、MuMu、Release、提交和推送不属于本轮授权。

本地证据：

- `PlayerPolicyTest 13/13` 与 `KiyoriSettingsPagesTest 8/8`，合计 `21/21`，零失败、零错误、
  零跳过
- `ci.test.test_player_assets 6/6`、`:app:compileDebugKotlin`、formal readiness 与
  `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，233 个任务零失败；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-28 20:26:27 +08:00`，
  大小 `459183137` 字节，SHA-256
  `8F2BD60B097A730C43221B286E0B6F4EB38ACA26AF2BE3E650FD13592B87F1A8`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名通过，
  `zipalign -c -P 16 -v 4` 为 `Verification successful`
- [PENDING] 目标设备上的页面逐项视觉、选择抽屉、profile 解码效果、GPU Next、Vulkan、
  Anime4K/倍速记忆和音量增强仍需真机验收
