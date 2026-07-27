# 下载中心与下载设置

[IN PROGRESS]

## 2026-07-26 旧版真实消费者矩阵

`kiyori-android@24a2dfa9` 的文件下载器设置按 `5/3/3/1` 四组共十二行排列。源码调用链确认这些设置不是统一占位：

| 旧版设置 | 旧版真实消费者 | 当前迁移状态 |
| --- | --- | --- |
| 自定义下载器 | `DownloadRequestDispatcher`、下载中心新增任务 | 第三内部阶段接入同一 HTTP 请求入口；内置归 Kiyori manager，系统归 Android `DownloadManager` |
| 自定义下载目录 | `InternalDownloadManager` 完成文件转存 | 第四内部阶段接入同一 settings/task owner；内置任务冻结 SAF 目标目录，系统下载器不消费该设置 |
| 同时下载任务数 | `InternalDownloadManager.schedulePendingDownloads` | 第一内部阶段接入现有 `BrowserDownloadManager` |
| 普通格式下载线程数 | HTTP Range 分段调度 | 已按用户确认收敛为 `3/6/12/20/32` 五档，默认 `6`；实际并发继续受文件大小与每段至少 `1 MiB` 约束 |
| M3U8 下载线程数 | M3U8 分片 semaphore | 已接入同一 manager 的资源 semaphore 与设置 UI |
| M3U8 自动合并 | M3U8 manifest 与本地包生成 | 已接入 playlist/companion runtime 与设置 UI |
| 自动转存公开目录 | 下载完成转存 | 内置下载默认落应用下载目录；新任务启用后完成时转存 `Download/Kiyori/browser/downloads/`，与 SAF 目录互斥 |
| 自定义下载分块大小 | 单流 buffer、Range 切片阈值 | 已由 manager 冻结并用于单流 buffer 与严格 Range 计划，设置 UI 已接入 |
| 安装包自动清理 | 完成 APK 清理 | 内置 APK 安装器成功唤起后按 90 秒延迟清理任务与文件，设置 UI 已接入 |
| 下载无需弹窗确认 | `DownloadRequestDispatcher` | 第三内部阶段接入唯一 pending-download request owner |
| 下载完成强提示 | `InternalDownloadManager` 完成/失败通知 | 第三内部阶段接入现有 manager 的完成/失败事件 |
| 切换下载协议 | OkHttp protocol 列表 | manager 按任务冻结值创建 Browser transport，设置 UI 已接入 |

旧下载中心“暂停下载”只显示“暂不支持暂停下载” Toast，不是真实能力；当前 `BrowserDownloadManager` 已有真实暂停/恢复，因此迁移后使用当前真实状态机。

## 串行内部阶段

每个内部阶段都必须先完成定向测试、正式开发门禁、`git diff --check`、Debug APK 构建和制品核验，前一阶段收口后才进入下一阶段。

1. [DONE-local] 下载内核基础：版本化 settings store、全局 FIFO 并发队列、1/2/4/8 HTTP Range 分段、恢复规范化、唯一 task snapshot flow
2. [DONE-local] 下载 UI 基础：浏览器紧凑抽屉继续复用现有任务；新增同 owner 下载中心、删除确认和 `5/3/3/1` 设置页面骨架，只让已有真实 consumer 的行可交互
3. [DONE-local] 下载请求策略：下载确认、完成提示、内置/系统下载器选择及系统任务边界
4. [DONE-local] 目标目录与文件操作：SAF 自定义目录、公开目录、重命名、移动、分享、复制地址与路径
5. [DONE-local] M3U8、transport 与完整下载设置页：5-A 至 5-D runtime、四项设置 UI、自动转存公开目录和 APK 自动清理均已接入，仍只使用现有 JSON record；真机与真实站点验收待完成
6. APK 清理与综合验收：明确目标集合、二次确认、恢复/并发/进度/文件动作全链路验证

## 旧实现

阶段开始时，`BrowserDownloadManager` 已支持 HTTP 分段、暂停、恢复、取消、重试、记录删除、状态恢复、速度、打开文件和打开位置，但每个任务立即启动且分段线程固定为 4。内部阶段 1 已把同一 manager 扩展为全局 FIFO 队列并发布唯一 task snapshot flow；页面仍只有浏览器子抽屉，没有全屏下载中心和真实设置页。

## Runtime 改造

- 保留现有 JSON task 格式并为新增设置建立独立版本化 preference store
- 增加全局任务调度队列，运行任务数不得超过 `maxConcurrentTasks`
- 分段计划使用 `segmentThreadCount`，只对服务端支持 Range 且文件大小满足条件的任务生效
- 暂停、取消、完成和失败后立即调度下一项 QUEUED 任务
- 应用恢复时把中断的活动任务规范化为可恢复状态，再由用户明确恢复；不自动发起网络请求
- 所有异常写入 `AppLogger` 和任务可见错误，不吞掉失败

## 内部阶段 1 本地证据

- `BrowserDownloadSettingsStore` 使用 schema version `1`；同时任务数只接受 `1..4`，Range 线程偏好只接受 `1/2/4/8`，删除文件默认值只作为后续确认框状态
- 调度器在锁内计算容量，用 `CoroutineStart.LAZY` 先登记 control 再启动；completion handler 覆盖启动前取消、完成、失败、暂停和取消后的容量释放
- FIFO 使用每次进入 `QUEUED` 时的 `updatedAt`，因此恢复和重试进入当前队尾；降低并发不会取消已运行任务
- 每个 HTTP 任务在创建时冻结线程偏好；实际分段按至少 `1 MiB`/段从 `1/2/4/8` 中选择，非 Range 与未知长度固定单线程
- 恢复时仍把 `QUEUED/CONNECTING/DOWNLOADING` 规范化为暂停；初始 settings flow 不会恢复网络请求
- 现有 JSON `tasks` map 仍是唯一 owner，`StateFlow<List<BrowserDownloadTaskRecord>>` 只是每次新增、变更和删除时发布的快照投影
- `BrowserDownloadPolicyTest` 6/6 通过；正式开发门禁与 `git diff --check` 通过
- Debug APK：`2026-07-26 12:57:25 +08:00`，`449485670` 字节，SHA-256 `C195752AFD4D81ADD7730C7D58AC5613198C0D208086518948E0BAD3A2488638`，`com.kiyori`，`45 / 0.1.0`，Android Debug v2 签名和 `zipalign -c -P 16 4` 通过
- 任务调度、HTTP Range、暂停恢复和进程恢复的真机网络行为仍为 `verification_pending`

## 下载设置

第一内部阶段设置全部直接约束当前 manager：

- 同时下载任务数：1 至 4
- 单任务分段线程数：1、2、4、8
- 下载目录：内置下载默认使用应用下载目录；可通过 SAF 选择持久化自定义目录并恢复默认目录，也可为之后的新任务开启公开目录自动转存
- 删除记录时是否同时删除文件：每次删除确认都由用户明确选择“仅删除记录”或“记录和文件”，不持久化默认选项

其余旧版真实设置保留在上述后续内部阶段，不在第一阶段伪造开关或宣称完成。

## 内部阶段 3 实现

- `BrowserDownloadSettingsStore` 继续使用 additive schema version `1`，新增默认 `INTERNAL`、默认开启完成提示和默认保留确认；持久化 engine ID 只接受 `internal` 或 `system`
- WebView 与 userscript 的 `http/https` 下载进入同一个请求函数。请求会冻结文件名、MIME、已知长度、下载器、Cookie、User-Agent 和 Referer；设置未跳过确认时，由 `StandardBrowserSessionTools.pendingBrowserDownloadRequest` 持有唯一临时状态
- Browser Home、展开的悬浮浏览器和最小化指示器观察同一 prompt。系统 Back 先取消确认，不修改 WebView 页面、历史或网络状态
- 内置模式只调用现有 `BrowserDownloadManager.startHttpDownload`；系统模式只调用 Android `DownloadManager.enqueue`，使用 `Download/Kiyori/browser/downloads/` 目标路径和系统完成通知，不向 Kiyori JSON、任务数、下载抽屉或 AI task event 写入镜像记录
- `blob:` 与 `data:` 不能由 Android `DownloadManager` 接管，继续使用现有 inline payload 路径；这属于下载类型的单一所有权，不是失败后的切换
- “下载完成强提示”只消费内置 manager 的 `completed/failed` 事件并显示 Toast，默认开启。系统任务完成 UI 始终由 Android 下载服务负责
- 内置或系统启动异常都写入 `AppLogger` 并显示失败信息，不再移植旧版“交给外部应用”逻辑
- 文件下载器设置保持 `5/3/3/1`，十二行均为真实可交互 consumer；每项的偏好、任务冻结和运行时边界见本文件的 5-E3 记录
- `BrowserDownloadPolicyTest` 9/9、`KiyoriSettingsPagesTest` 6/6，共 15/15 通过
- Formal readiness 与 `git diff --check` 通过；最终 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 5m 33s`
- Debug APK：`2026-07-26 15:10:14 +08:00`，`449493010` 字节，SHA-256 `F28428B5FC9E8A71E9F66EEE8095619B5EF49BFA9AFA6FAC62DC0F4933E14874`，`com.kiyori`，`45 / 0.1.0`，Android Debug v2 签名和 `zipalign -c -P 16 4` 通过
- Browser Home/悬浮 prompt 视觉、Android 系统下载目录与通知、Cookie/Referer 下载、完成/失败 Toast 仍为 `verification_pending`

## 内部阶段 4 实现与本地证据：目标目录与文件操作

本阶段只处理旧版 `InternalDownloadManager` 已有消费者对应的文件位置与完成任务动作，不进入 M3U8、播放器或浏览器嗅探。

### 位置模型

- `BrowserDownloadSettingsStore` 增加自定义目录 URI 与显示名称；保持 schema `1`，因为新字段是同一偏好记录的加法，不创建迁移分支或第二 store
- 内置任务创建时读取并冻结当前自定义目录 URI；正在下载的任务不因设置页改变而换目标
- `BrowserDownloadTaskRecord` 增加可选 `destination_uri`。旧 JSON 缺少该字段时继续使用已有 `destination_path`；任务仍只由 `browser_download_tasks.json` 的同一 `tasks` map 持有
- 网络分段与 `.part` 文件继续落在现有 staging 目录。完成时写入冻结的 SAF 目录，写入成功后才删除 staging 文件并发布 `COMPLETED`
- SAF 目录失效、创建文件失败或复制中断时，任务发布 `FAILED` 与可见错误，不改存默认目录，也不吞掉异常
- 系统下载器不接管 SAF URI，继续由 Android `DownloadManager` 持有系统任务和通知；自定义目录设置只约束内置下载器

### 完成任务动作

只对 `COMPLETED` 任务提供旧版同语义动作：

- 重命名、修改后缀：文件路径使用文件系统原子改名； `content://` 使用 `DocumentsContract.renameDocument`
- 修改文件夹：通过 `OpenDocumentTree` 获取并持久化目标目录，复制成功后删除原位置，再更新 `destination_uri`/`destination_path`
- 复制下载链接、复制文件路径：优先复制真实源 URL 与真实本地位置； SAF 文件没有绝对路径时复制其 `content://` URI
- 分享本地文件：路径使用现有 `FileProvider`，SAF 使用原始 `content://`，均只授予临时读取权限
- 转存公开目录：复制到 `Download/Kiyori/browser/downloads/` 后才删除原位置；已在该目录的文件不执行无意义覆盖

### 设置页与 UI

- “自定义下载目录”点击后显示旧版选择面板：`选择目录`、已有目录时追加 `恢复默认目录`
- 下载抽屉继续使用同一 `BrowserDownloadManager.taskSnapshots`；完成卡片增加旧版动作入口，删除始终二次确认并由用户本次明确选择是否同时删除文件
- `KiyoriDownloadSettingsPage` 不再把目录做成说明型空子页；目录选择器的权限错误必须写 `AppLogger` 并显示可见失败提示

### 阶段 4 本地证据

- `BrowserDownloadSettingsStore` 已持有 SAF 目录 URI 与显示名称，并支持恢复默认目录；内置任务创建时把当前目标目录冻结到同一任务记录
- 任务 JSON 的兼容解析继续接受缺少 `destination_uri` 和 `target_directory_uri` 的旧记录；这一点由源码解析分支审计确认，没有把 JVM 纯逻辑测试表述为真实 JSON 往返测试
- HTTP 分段和 `.part` 文件继续使用既有 staging 目录；完成时直接写入默认文件或冻结的 SAF 目录，SAF 写入失败会把任务置为 `FAILED`，不会改存默认目录
- 完成任务已接入重命名、修改后缀、移动到 SAF 目录、复制下载链接、分享文件、复制路径或 `content://` URI，以及转存 `Download/Kiyori/browser/downloads/`
- 路径文件分享使用现有 `FileProvider`，SAF 文件分享使用原始 URI；移动和转存都只在复制成功后删除源文件
- `BrowserDownloadPolicyTest` 覆盖自定义目录默认值、重命名/后缀规则和 URI/路径显示规则；`KiyoriSettingsPagesTest` 锁定目录选择器接线，`KiyoriShellStateTest` 保持共享下载抽屉状态合同
- `BrowserDownloadPolicyTest` 11/11、`KiyoriSettingsPagesTest` 6/6、`KiyoriShellStateTest` 31/31，共 48/48 通过
- Formal readiness 与 `git diff --check` 通过；最终 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 3m 14s`，230 个任务零失败
- Debug APK：`2026-07-26 17:08:19 +08:00`，`449493010` 字节，SHA-256 `75DB5E0A545DCE71A61F396A9E58163D918DAD9752C44FEB22D695C90C1DE3A4`，`com.kiyori`，`45 / 0.1.0`，`minSdk 26`、`targetSdk 34`、`compileSdk 36`，Android Debug v2 签名和 `zipalign -c -P 16 -v 4` 通过
- SAF 目录授权、实际文件移动、系统分享、公开目录转存和 Android 设备文件提供者行为仍为 `verification_pending`

## 内部阶段 5 设计冻结：M3U8 与 Browser transport

本阶段已经完成 `kiyori-android@24a2dfa9` 与当前代码的只读调用链对照。实现只扩展 `BrowserDownloadManager`、同一 JSON 任务记录和同一下载设置 store；不复制旧版 Room 数据库，不改动 `StandardFileSystemTools` 仍在使用的通用 `HttpMultiPartDownloader`，也不进入播放器、嗅探或 APK 清理。

### 旧版已确认语义

- “M3U8自动合并”不是把 TS 分片转码或拼接为 MP4；它下载最高 `BANDWIDTH` variant 的媒体 playlist、媒体分片、`#EXT-X-KEY` 和 `#EXT-X-MAP` 资源，把 URI 改写为本地文件 URI，并保存为 `<playlist>.m3u8` 加 `<playlist>.m3u8.files/` 的离线包
- master playlist 最多递归四层，每层选择 `BANDWIDTH` 最大的 variant；媒体 playlist 中的注释和空行保持原顺序
- 关闭自动合并时只保存服务端返回的 playlist 文本，不下载离线资源包
- 旧版 M3U8 资源下载使用 `Semaphore(m3u8ThreadCount)`；设置选项为 `3/8/16/20/32/48/64`，默认 `16`
- 旧版分块大小选项为 `12288/8192/4096/2048/1024/512/256 KiB`，默认 `2048 KiB`；它同时用于单流 buffer 和普通 Range 切片大小
- “优先 HTTP/2”明确配置 `HTTP_2 + HTTP_1_1`，而“仅 HTTP/1.1”只配置 `HTTP_1_1`；默认开启 HTTP/2
- M3U8 离线包必须保留在应用下载目录。旧版允许连同 companion directory 一起重命名，但“修改文件夹”和“转存公开目录”会明确拒绝该离线包

### 唯一 owner 与 JSON 扩展

- `BrowserDownloadTaskRecord.type` 继续表达当前调度的网络/内联机制；M3U8 仍是同一个 HTTP 任务，不创建第二类 task map
- 新任务在创建时冻结 `m3u8_thread_count`、`auto_merge_m3u8`、`chunk_size_kb` 和 `enable_http2`；排队、暂停和恢复期间修改设置不会改变已存在任务的网络计划
- 任务记录增加 `is_m3u8_package`，只在内置下载器确认 M3U8 且启用自动合并后置为真；完成记录、删除、重命名和文件动作都从该字段判断 companion directory 所有权
- 旧 JSON 缺少新增字段时保留已创建任务的既有普通 HTTP 语义：`auto_merge_m3u8=false`、`is_m3u8_package=false`；其余冻结值使用 `16 / 2048 KiB / HTTP2 enabled`。新创建任务使用设置页当前值，默认自动合并开启
- `thread_count` 固定为普通 HTTP 并行上限，不再改写为 Range 分片文件总数；分块大小可以生成多个 part，实际并发由该冻结线程数的 semaphore 限制
- settings schema 继续为 `1`，新增字段是同一偏好记录的加法；不创建迁移 store 或并行配置源

### Browser 专用 OkHttp transport

- 浏览器下载从 `HttpMultiPartDownloader` 切换到内部专用 OkHttp transport；通用文件系统工具继续使用原 helper，两者不共享下载任务状态
- transport 统一处理 HEAD 元数据请求、必要的 `GET Range: bytes=0-0` 探测、最终重定向 URL、响应 MIME、严格 Range 响应、单流读取、分片读取和 M3U8 文本/资源请求
- Range part 必须收到 `206` 且长度与计划一致；服务器忽略 Range 返回 `200` 时任务显式失败，不把完整响应写入多个 part
- 普通 Range 切片大小使用 `max(所选 chunkSize, 1 MiB)`，保留第一内部阶段已经验收的“每段至少 1 MiB”合同；单流 buffer 使用设置页选择的真实大小
- 普通 part 与 M3U8 资源都只对同一 URL 做旧版同等的 5 次有界重试和递增等待；不切换下载器、不改协议设置、不更换目标目录
- OkHttp client 的 dispatcher 与 connection pool 由当前任务冻结的普通/M3U8 线程上限和设置 store 的任务并发共同约束；HTTP/2 开关只控制显式 protocol 列表
- 当 M3U8 线程数导致 `128 / max(normalThreads, m3u8Threads)` 小于当前任务并发时，设置 store 同步收窄 `maxConcurrentTasks`。降低限制不取消已运行任务，只影响后续调度

### M3U8 离线包生命周期

- 通过 URL 后缀、建议文件名或响应 MIME 判断 M3U8；只有内置下载器进入离线包流程，Android `DownloadManager` 仍完整持有系统任务
- 自动合并开启时，playlist staging 文件继续位于现有 part 路径，资源写入最终应用目录文件旁的 `<fileName>.files/`；成功后把改写后的 playlist 合并为应用下载目录文件
- 离线包不消费 SAF 自定义目录，也不消费自动公开转存。任务被确认为离线包时，目标位置固定为应用下载目录，这是该内容类型的固定位置合同，不是写入失败后的改存行为
- 任一 variant、KEY、MAP 或媒体分片失败时，任务进入 `FAILED`，清理未完成 playlist 和 companion directory；暂停或取消停止网络，恢复时从同一任务重新生成离线包
- 完成字节数为 playlist 文件与 companion directory 全部文件的总和；下载中总大小未知时保持 indeterminate，只发布已完成资源的累计字节
- 自动合并关闭时，M3U8 按普通单文件保存，可使用任务创建时冻结的默认或 SAF 目录，不创建 companion directory

### 完成任务文件动作

- M3U8 离线包重命名必须先确认新 playlist 和新 companion directory 都不存在；playlist 改名成功后再改 companion directory，第二步失败时恢复 playlist 原名
- 删除离线包同时删除 playlist、part 和 companion directory
- “修改文件夹”和“转存公开目录”对离线包显示旧版同语义错误“`M3U8离线包需要保留在应用下载目录`”；复制链接、复制路径、打开、分享和重命名继续使用完成记录
- 普通文件及未自动合并的 M3U8 文本继续使用第四内部阶段已经验收的 SAF、分享和转存语义

### 设置页与验证门禁

- `M3U8下载线程数` 接入旧版七个选项；`M3U8自动合并` 接入真实 toggle；`自定义下载分块大小` 接入旧版七个选项与 `KB/MB` 显示；`切换下载协议` 接入“优先 HTTP/2 / 仅 HTTP/1.1”选择面板
- “自动转存公开目录”和“安装包自动清理”已在完整设置页切片中接通；系统 DownloadManager 任务不进入这两个内置 manager consumer
- 纯逻辑测试覆盖设置默认值/选项/并发上限、chunk Range 计划连续性、最高带宽 variant、相对 URL、KEY/MAP 改写、离线包命名和旧 JSON 默认语义
- transport 测试使用本地可控 HTTP server 验证 `206`、忽略 Range 的 `200`、HTTP 错误、重定向、MIME、M3U8 资源失败与有界重试；不以公网下载代替确定性测试
- 实现完成后依次运行定向 JVM 测试、正式开发门禁、`git diff --check`、Kotlin 编译和唯一一次 Debug APK 构建；真实 HTTP/2 协商、站点鉴权、M3U8 加密播放、暂停恢复和大文件 I/O 保持 `verification_pending`

### 内部阶段 5-A 实现：设置 owner 与纯策略

- `BrowserDownloadSettingsStore` 保持 schema version `1`，新增七档 `m3u8ThreadCount`、默认开启的 `autoMergeM3u8`、七档 `chunkSizeKb` 和默认开启的 `enableHttp2`；这些字段当前只完成持久化 owner，不提前开放设置页交互
- `setM3u8ThreadCount` 与 `setMaxConcurrentTasks` 共同执行 `min(4, 128 / max(normalThreads, m3u8Threads))` 上限；M3U8 线程提高导致上限收窄时，同一次 preference editor 写入线程数和新的任务并发值，不取消已经运行的任务
- 新增纯策略 `BrowserDownloadM3u8Policy.kt`：chunk Range 计划把所选块大小与 `1 MiB` 取较大值，生成连续 inclusive ranges；M3U8 检测复用 URL、建议文件名和响应 MIME 三类信号
- master playlist 选择器解析标准 `#EXT-X-STREAM-INF:` 冒号与后续逗号属性，选择最高 `BANDWIDTH` variant 并使用 `URI.resolve` 解析相对地址；无效 URI 直接失败，不生成替代地址
- media playlist 改写保持注释和空行顺序，为 `#EXT-X-KEY`、`#EXT-X-MAP` 与媒体行生成 `resource_N` / `segment_N` 本地文件计划，并保留旧版扩展名与 `<playlist>.files` companion directory 规则
- `BrowserDownloadPolicyTest` 当前为 16/16 通过，覆盖设置默认值/合法选项/动态并发、chunk Range 连续性与 `1 MiB` 下限、M3U8 检测、最高带宽、相对 URL、KEY/MAP 改写和离线包命名
- 本批次没有修改 `BrowserDownloadManager` 网络路径、任务 JSON、`KiyoriDownloadSettingsPage` 交互或 Android `DownloadManager` 边界；这些仍属于阶段 5 的后续独立构建批次

### 内部阶段 5-B 实现：Browser 专用 OkHttp transport

- 新增 `BrowserDownloadTransport`，配置在创建 dispatcher、connection pool 和 client 前完成严格校验；HTTP/2 开启时协议列表为 `HTTP_2 + HTTP_1_1`，关闭时只允许 `HTTP_1_1`
- transport 统一持有 HEAD 元数据、HEAD `405/501` 后的 `GET Range: bytes=0-0` 探测、最终重定向 URL、响应 MIME、单流、严格 Range、M3U8 文本和 64 KiB 资源下载
- Range 必须收到 `206`、有效且与请求完全一致的 `Content-Range`，响应体长度必须与 inclusive range 一致；忽略 Range 的 `200`、缺失/错误边界及过短/过长响应都会显式抛出 `IOException`
- 同一 URL 最多尝试五次，等待为 `250/500/750/1000 ms`；每次失败和最终失败都会把文件恢复到调用前长度，并回滚本次上报的进度，取消不会被网络重试吞掉
- transport 的 dispatcher 并发上限由冻结配置的同时任务数和普通/M3U8 线程最大值共同决定；本批没有把 transport 接入 `BrowserDownloadManager`，因此现有用户下载仍继续走原网络路径
- `BrowserDownloadTransportTest` 使用 JDK loopback `HttpServer` 覆盖协议列表、HEAD、405 Range 探测、重定向/MIME、正确 `206`、忽略 Range、缺失/错误 `Content-Range`、短/长响应、第五次成功、永久五次失败、M3U8 文本/资源和 append 回滚，共 12/12 通过
- `BrowserDownloadPolicyTest` 16/16 与 transport 测试合计 28/28 通过；Formal readiness 和 `git diff --check` 通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 3m 29s`，230 个任务零失败；APK 于 `2026-07-26 19:01:15 +08:00` 生成，`449493010` 字节，SHA-256 `835CCF7728BEF51E3B50D9717DBCB0139F0297654BB138D3E1F9B7A46551E6AA`
- APK 为 `com.kiyori`、`45 / 0.1.0`、`minSdk 26`、`targetSdk 34`、`compileSdk 36`，Android Debug v2 签名与 `zipalign -c -P 16 -v 4` 通过；真实 HTTP/2 协商、大文件网络和设备存储仍为 `verification_pending`
- 下一批只接 `BrowserDownloadManager`、同一 JSON 任务冻结字段和普通严格 Range 调度；该批已作为 5-C 完成，M3U8 离线包运行时与设置页交互继续按依赖在后续独立构建批次完成

### 内部阶段 5-C 实现：manager 普通 HTTP 接线

- `BrowserDownloadTaskRecord` 在同一 JSON record 增加 `m3u8_thread_count`、`auto_merge_m3u8`、`chunk_size_kb`、`enable_http2` 和 `is_m3u8_package`；新 HTTP 任务只读取一次当前设置并冻结，旧 JSON 缺字段时使用 `16 / false / 2048 KiB / true / false`，继续保持普通 HTTP 任务语义
- `thread_count` 现在固定表达普通 HTTP 并发上限，不再被改写为 part 数量；新任务的 Range part 按 `max(chunkSize, 1 MiB)` 生成，实际同时请求数由 `Semaphore(thread_count)` 约束
- 每个运行中的 HTTP 任务创建一个 `BrowserDownloadTransport`，HEAD/Range 探测和全部 part 共享同一 OkHttp client；探测后的最终重定向 URL只用于本次网络请求，原始 `source_url` 继续用于任务记录、复制链接和下一次恢复探测
- 非 Range 或未知长度任务使用一条 stream，buffer 直接使用冻结的 `chunk_size_kb`；已知长度单流会严格核对响应体总长
- Range 请求现在必须同时匹配请求起止、探测总长和响应体长度；已有 part 超过计划长度、完成 part 长度错误或合并输入缺失都会显式失败，不再接受服务器忽略 Range 的完整 `200` 响应
- 每个失败尝试由 transport 回滚本次文件和进度；暂停/取消继续使用现有唯一 control 与任务状态机。已有合法 part 可从原长度继续请求剩余 Range，非 Range 恢复会清理其 staging 文件并重新开始
- 本批只接普通 HTTP。任务冻结 M3U8 网络字段但 `is_m3u8_package` 仍保持 false；M3U8 runtime 在 5-D 完成，自动转存、分块、协议和 APK 清理设置 consumer 在 5-E3 完成
- `BrowserDownloadPolicyTest` 17/17、`BrowserDownloadTransportTest` 12/12，共 29/29 通过；`compileDebugUnitTestKotlin`、Formal readiness 与 `git diff --check` 通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 33s`，230 个任务零失败；APK 于 `2026-07-26 19:24:10 +08:00` 生成，`449493010` 字节，SHA-256 `0FD80C344F5E44D22F655A96F1C8EC13CF2C7594878D7414B08CB0D3362B827D`
- APK 为 `com.kiyori`、`45 / 0.1.0`、`minSdk 26`、`targetSdk 34`、`compileSdk 36`，Android Debug v2 签名与 `zipalign -c -P 16 -v 4` 通过；真实站点下载、暂停恢复、Cookie 鉴权、HTTP/2 协商和大文件 I/O 仍为 `verification_pending`
- 下一独立批次实现 M3U8 master/media playlist、离线 companion directory、资源并发下载和失败清理；构建完成后再接下载设置 UI

### 内部阶段 5-D 实现：M3U8 离线包 runtime

- M3U8 任务在 Browser transport 探测后进入独立执行路径；自动合并关闭时保存服务端返回的原 playlist 文本，自动合并开启时最多递归四层 master playlist，并在每层选择最高 `BANDWIDTH` variant
- 自动合并开启时，`#EXT-X-KEY`、`#EXT-X-MAP` 和媒体分片使用同一任务 transport 与冻结的 `m3u8ThreadCount` semaphore 并发下载，保存到 `<playlist>.m3u8.files/`，然后把 playlist URI 改写为本地 `file:` URI；完成字节数为 playlist 与 companion directory 文件总和
- M3U8 离线包一经确认即固定写入 `Download/Kiyori/browser/downloads/`，不消费任务冻结的 SAF 目录；资源失败、暂停、取消或完成合并失败都会清理未完成 playlist、staging part 和 companion directory，并回滚本轮进度
- 已完成离线包可打开、分享、复制链接/位置和重命名；重命名同步 playlist 与 companion directory 并更新本地 URI。移动到 SAF 和转存公开目录明确拒绝，删除记录按既有确认语义同时清理离线包文件
- 新增 `BrowserDownloadM3u8RuntimeTest` loopback 合同测试：自动合并/最高带宽、原 playlist 保存、资源失败清理/进度回滚、四层深度限制和成对重命名共 5/5；与现有 policy 17/17、transport 12/12 合计 34/34 通过
- 本批 `compileDebugUnitTestKotlin` 和定向 JVM 测试已通过；`:app:assembleDebug` 为 `BUILD SUCCESSFUL in 3m 19s`，230 个任务零失败。APK 于 `2026-07-26 20:05:12 +08:00` 生成，`449493010` 字节，SHA-256 `347DC515CE20CF91C83F9A96C1031BE159EDD79E49DB650EC2066F652128D3C3`，Debug V2 签名和 16KB zipalign 通过
- 真实加密 HLS、站点鉴权、长时间暂停恢复、HTTP/2 协商和设备文件行为仍为 `verification_pending`。四项设置 UI 交互在下一独立构建批次接入

### 内部阶段 5-E1 设计：M3U8 下载线程数 UI consumer

- 本切片只接入“`M3U8下载线程数`”，不同时实现 M3U8 自动合并、分块大小、下载协议、自动转存或安装包清理的设置 UI
- 设置行显示 `BrowserDownloadSettings.m3u8ThreadCount` 当前值；点击后沿用现有选择面板，标题严格为“`下载线程数，当前：N`”
- 选择项只来自 `BROWSER_DOWNLOAD_M3U8_THREAD_OPTIONS` 的 `3/8/16/20/32/48/64`，当前值显示勾选；选择后只调用 `BrowserDownloadSettingsStore.setM3u8ThreadCount`
- store 已在同一次 preferences editor 中保存线程数并通过 `resolveBrowserDownloadMaxConcurrentTasksLimit` 收窄 `maxConcurrentTasks`，因此不新增设置 owner、迁移 schema 或 UI 本地副本
- “同时下载任务数”选择面板必须按当前普通/M3U8 线程数显示 `1..limit`，避免 `48/64` 线程状态下暴露会被 store 拒绝的 `3/4`
- 已运行任务继续使用创建时冻结的线程计划，不取消、不重启；设置变化只影响后续调度容量和之后创建的 HTTP/M3U8 任务
- UI contract 测试锁定该行从占位导航变为真实 `SELECT_M3U8_THREAD_COUNT` consumer；现有 policy 测试继续锁定七档选项、默认 `16` 与动态并发上限

### 内部阶段 5-E2 设计：M3U8 自动合并 toggle

- 本切片只接入“`M3U8自动合并`”，继续保持分块大小、下载协议、自动转存公开目录和安装包自动清理的现有占位状态
- 设置行复用旧版同语义的整行点击和 `17dp` 方形勾选框，不新增 Material Switch、说明弹窗或第二份 UI 状态
- 勾选状态直接读取 `BrowserDownloadSettings.autoMergeM3u8`；点击只调用 `BrowserDownloadSettingsStore.setAutoMergeM3u8(!current)`，由同一 `StateFlow` 刷新界面
- 默认值保持旧版与当前 store 的 `true`；不修改 schema、偏好 key、任务 JSON 解析或旧任务缺字段时的 `false` 语义
- 设置变化只影响之后创建的任务。新任务冻结该值；已排队、暂停、下载中和完成任务都不被重写、不取消、不重新调度
- 关闭时新 M3U8 任务只保存服务端 playlist 文本；开启时新任务继续使用已经验收的最高带宽 variant、KEY/MAP/媒体资源和 companion package 运行时
- UI contract 测试锁定该行从 `NONE` 变为 `TOGGLE_AUTO_MERGE_M3U8`；现有 policy 测试继续锁定默认开启，runtime 测试继续覆盖开启和关闭两条真实执行路径

### 内部阶段 5-E3 完整文件下载器设置页切片

- 本切片收口固定提交中 `DownloadSettingsContent` 的全部十二行：自动转存公开目录、分块大小、安装包自动清理和下载协议不再是空占位；页面移除本页专用的空子页与不可交互 toggle。
- `BrowserDownloadSettingsStore` 新增 `autoTransferToPublicDirectory` 与 `autoCleanApk` 两个 owner 字段，schema 继续为 `1`。选择 SAF 目录会原子关闭自动公开转存；开启自动公开转存会清除 SAF 目录，两者不可同时存在。
- 内置普通下载和未自动合并的 M3U8 playlist 默认写入应用下载目录；自动公开转存只对非 M3U8 离线包的新任务生效，完整文件复制到 `Download/Kiyori/browser/downloads/` 后才删除应用目录源文件。M3U8 离线包及系统 DownloadManager 任务不消费该开关。
- 新 HTTP/内联任务冻结自动转存、分块大小和 HTTP 协议；既有 JSON 缺少自动转存字段时使用 `false`，已运行、已排队或已完成任务不因设置变化改写目标。
- 打开 Kiyori 内置 APK 且自动清理开启时，manager 在安装器成功唤起后等待 `90s`，再删除当前任务记录和当前文件；系统下载器不进入该生命周期。
- UI 复刻旧版选择面板：分块大小为 `12MB/8MB/4MB/2MB/1MB/512KB/256KB`，协议为“优先 HTTP/2/仅 HTTP/1.1”；四个 toggle 复用旧版整行点击与 `17dp` 方形勾选框。
- `KiyoriSettingsPagesTest` 锁定 `5/3/3/1` 组结构与十二项 action 全部接通；`BrowserDownloadPolicyTest` 锁定新默认值、互斥目标、APK 识别和 `90s` 生命周期常量。

### 内部阶段 5-E4 设置子页面视觉统一

- 文件下载器根标题改为“文件下载器设置”，删除旧“下载器及自定义”标题
- 与网页浏览器设置共用 `KiyoriCollapsingSettingsPage`：固定返回键和真实状态栏 inset，标题在前 `72dp` 滚动内从 `32dp / 60dp / 26sp` 连续移动缩小到 `56dp / 16dp / 20sp`，随后固定吸顶
- 四组下载设置卡移除最外围描边，内部行、分隔线、选择面板、方形勾选框和十二项真实 consumer 保持不变
- 共享容器统一 `14dp` 分组间距与 `28dp` 底部留白，作为后续 Kiyori 设置子页面的默认视觉基线
- `KiyoriSettingsPagesTest` 锁定两页最终标题、展开/中间/吸顶三帧和折叠进度边界；真机滑动轨迹、字体缩放和横竖屏视觉仍需设备验收

### 内部阶段 5-E5 下载选项与选择抽屉微调

- “同时下载任务数”候选上限从 `4` 提升为 `8`。默认普通线程 `6` 与 M3U8 线程 `16` 对应完整 `1..8`；选择更高线程时仍按 `128 / max(normalThreads, m3u8Threads)` 同步收窄，最高上限改为 `8`
- 普通格式线程候选严格为 `3/6/12/20/32`，默认 `6`；M3U8 候选继续保持 `3/8/16/20/32/48/64` 与默认 `16`
- 普通线程和 M3U8 线程变化都在同一次设置写入中规范 `maxConcurrentTasks`，避免持久化组合在重启后违反 transport 的总请求预算
- 普通 Range 的实际 semaphore permit 取所选线程数与 `totalBytes / 1 MiB` 的较小值；不支持 Range、未知长度或不足 `1 MiB` 时仍使用单线程
- 选择抽屉标题与取消区垂直 padding 改为 `13dp`，选项为 `10dp` 且保持最小 `44dp` 点击高度，勾选图标改为 `20dp`；八项并发列表不再占用过高面板
- 本轮只调整这些候选与选择抽屉密度，不改变 M3U8 下载算法、队列 owner、任务冻结、SAF、自动转存、APK 清理或系统下载器边界
- 本地验证完成：五组定向 JVM 测试 `50/50`，Formal readiness 与 `git diff --check` 通过；`:app:assembleDebug` 完成 `230` 个任务。`app-debug.apk` 生成于 `2026-07-26 23:28:34 +08:00`，大小 `449493010` bytes，SHA-256 `425E2CE64C6262A0AFF8F16B43D13F395E5BF444FFAB34694F468D62E95DC556`，`com.kiyori 45/0.1.0`，Debug V2 签名及 16KB ZIP 对齐通过。设置抽屉密度、两个齿轮入口和首页加号仍需真机视觉与触摸验收

### 自问自答

#### 为什么不直接把旧版 `InternalDownloadManager` 搬进来？

当前 JSON manager 已持有浏览器、AI 事件、队列、暂停恢复、SAF 和下载中心。搬入旧 Room manager 会形成第二任务 owner；只迁移经过源码确认的 transport 与 M3U8 算法，状态仍归现有 manager。

#### 为什么不把离线包移动到 SAF？

playlist 的本地 URI 指向同目录 companion files。单独复制 playlist 会破坏离线播放，而 SAF tree 内跨文档 URI 的整包改写和原子迁移并非旧版已有能力，因此严格保留旧版“离线包留在应用下载目录”合同。

#### 为什么所选 256/512 KiB 不会产生小于 1 MiB 的 Range part？

第一内部阶段已把 1 MiB 最小 part 作为当前 manager 的恢复和 JSON 规模安全边界。较小设置仍真实控制单流 buffer；Range 切片取设置值与 1 MiB 的较大值，避免破坏已经验收的当前合同。

#### 为什么协议设置必须更换 Browser transport？

当前 `HttpURLConnection` helper 没有每个任务的 OkHttp `Protocol` owner，无法真实实现“优先 HTTP/2 / 仅 HTTP/1.1”。Browser 专用 OkHttp transport 才能让设置直接约束连接，而不影响仍使用通用 helper 的其他文件工具。

## 旧全屏下载中心设计（已废止）

旧三筛选全屏页面、宽屏双列任务卡和 nested Download Settings 路由已经被统一下载抽屉方案替代。负一屏和浏览器菜单现在打开同一双页签内容，继续读取唯一 `BrowserDownloadManager` snapshot；设置首页仍直接进入 Download Settings。

## 2026-07-27 浏览器下载下拉抽屉正式复刻切片

### 范围与参考

- `D:\03_Default\图片\Kiyori\1.jpg` 至 `7.jpg` 是本切片的视觉权威；`D:\10_Project\kiyori-android` 只提供 Compose 结构与已实现交互参考，`D:\10_Project\hikerView` 用于核对真实下载业务语义和不同任务类型的操作矩阵
- 保留 `WebSessionBrowserBottomDrawer` 的位置、部分展开/完全展开状态、拖动阈值、遮罩和弹簧动画；本切片只替换下载抽屉内容，不建立新的抽屉宿主
- 当前界面按未发布方案迭代处理：旧三筛选紧凑抽屉彻底替换为“已下载 / 下载中”双页签；旧全屏 `KiyoriDownloadCenterPage`、`DOWNLOAD_CENTER` Shell 路由、旧任务卡和三筛选投影全部删除，负一屏与浏览器菜单打开同一共享下拉抽屉
- `BrowserDownloadManager` 继续是唯一 Kiyori 任务 owner，`BrowserDownloadSettingsStore` 继续拥有下载器、并发、线程、目标目录、确认、提示、M3U8、HTTP 协议与 APK 清理设置；Android `DownloadManager` 任务不镜像进 Kiyori 列表

### UI 与交互

- 顶部严格保留“我的下载”、四竖线菜单、“新增”“清空”；菜单包含排序方式、批量删除、文件管理、显示时间、分类显示和更多设置
- “已下载 / 下载中”字体与页签垂直留白收紧，绿色指示线贴近文字，标题行与页签整体压扁以增加任务列表可用高度；顶部菜单宽度和单行高度同步收紧
- 顶部菜单使用显式透明全屏点击层；菜单 Surface 自身消费点击，点击菜单周围任意区域立即关闭，不依赖平台窗外点击判定
- Shell 共享抽屉宿主只在抽屉可见或退出动画尚未完成时参与组合；完全隐藏后移除 `zIndex(30)` 的全屏命中树，避免透明遮罩截断软件首页 Pager 与全部按钮触摸
- 页签固定为“已下载 / 下载中”。已下载只显示 `COMPLETED`，下载中统一承载排队、连接、下载、暂停、失败和取消状态；页签切换时清理批量选择
- 排序提供最新、最早和名称；分类按 MIME 与后缀稳定归入视频、音频、图片、应用、压缩包、文档和其他，只改变当前投影，不写入任务 JSON
- 任务卡保持紧凑：文件名、来源、状态、大小/进度/速度、可选时间和分类；点击已完成任务打开文件，长按按任务格式和状态打开对应操作面板，批量模式只切换显式目标集合
- “新增”弹窗包含文件名、URL、后缀、内置/系统下载器。初始下载器读取当前设置；用户本次显式选择只决定本次任务，内置任务仍冻结当前下载设置，系统任务直接交给 Android `DownloadManager`
- “清空”与批量删除必须二次确认。删除记录始终清理任务拥有的临时分片；是否删除已完成文件由本次明确选择决定，不静默改写任务或系统下载
- “文件管理”调用真实下载位置入口；“更多设置”通过 Shell 的 Download Settings 路由打开同一个设置 owner，overlay 先最小化并继续保留活动 WebView

### 真实业务入口

- `BrowserDownloadItem` 只扩充 manager record 的只读投影：来源 URL、MIME、创建/完成时间、M3U8 离线包标志和重新下载能力；不复制 headers、分片或持久化状态
- 手动下载统一校验 `http/https` URL，按文件名、URL 与用户后缀生成目标名和 MIME；内置分流调用现有 `startHttpDownload`，系统分流调用现有 Android `DownloadManager` 请求构造
- 手动下载具有明确的浏览器会话与独立入口两种请求上下文。浏览器会话上下文携带当前 Profile 的 Cookie、User-Agent 与 Referer；负一屏首次打开且没有活动 WebSession 时使用独立请求 ID 与空页面头，不创建隐藏 WebView、悬浮层或第二套下载 owner
- 已完成普通网络任务的“重新下载”创建一个新的 manager task，保留原记录；只有具备非空 `http/https` 来源的任务显示该动作
- 普通已完成文件按 `7.jpg` 提供删除、批量删除、重新下载、重命名、修改后缀、修改文件夹、复制下载链接、分享本地文件、复制文件路径和公开目录转存；缺少网络来源的任务不显示重新下载或复制链接
- M3U8 离线包使用独立操作集合，只保留删除、批量删除、重新下载、重命名、复制下载链接和合并 MP4，不显示修改后缀、SAF 移动、分享单个 playlist、复制 playlist 路径或公开目录转存
- 活动和暂停任务按 `6.jpg` 提供 manager 已真实实现的暂停、继续、取消、删除和批量取消；失败、取消任务改为恢复、删除和批量删除。`hikerView` 的“边下边播”依赖本地代理端点与播放器 owner，当前 Kiyori 尚无该调用链，本切片不显示无 consumer 的按钮
- 长按操作集合由纯策略解析器按完成普通文件、完成 M3U8 离线包、活动任务、暂停任务、失败/取消任务分别生成；UI 只把动作枚举绑定到 manager consumer，测试锁定不同格式和状态不会出现错误按钮或重复“恢复下载”
- “合并为MP4格式”仅对已完成、路径型 M3U8 离线包显示。FFmpeg 读取本地重写后的 playlist，输出到同目录临时文件并原子改名；同一任务记录切换到 MP4 是提交点，源离线包仅在提交后清理，清理异常不删除已提交的 MP4

### 验证门禁

- 纯 JVM 测试覆盖双页签、排序、分类、手动文件名/后缀解析、批量目标选择、重新下载能力和 MP4 输出命名
- 定向测试后执行 `git diff --check`、Formal readiness 与 `:app:assembleDebug`；核验 Debug APK 路径、大小和 SHA-256
- 本地构建不替代抽屉拖动、长按、输入法、系统下载器、文件选择器、FFmpeg 实际媒体和真机视觉验收，缺失项保持 `verification_pending`

### 本轮实现与本地证据

- 下载抽屉已替换为双页签实现，保留原 `WebSessionBrowserBottomDrawer` 的展示位置、拖动、部分/完全展开和弹簧动画；负一屏通过 Shell 状态打开同一抽屉，旧全屏中心、三筛选投影和任务卡已删除
- 普通文件、M3U8 离线包、活动/暂停任务和失败/取消任务已使用不同长按操作集合；普通文件面板与 `7.jpg` 的十项顺序一致，活动任务实现暂停、取消和批量取消，未把缺少代理与播放器 consumer 的“边下边播”做成空动作
- 手动新增、内置/系统分流、已完成网络任务重新下载、设置路由和 M3U8 本地 remux 已接入唯一 manager；M3U8 合并以任务记录切换到 MP4 为提交点，提交后源包清理失败只记录明确日志，不删除已提交 MP4
- `git diff --check` 通过；`:app:compileDebugKotlin` 通过。定向 JVM 测试为 `BrowserDownloadPolicyTest 19`、`BrowserDownloadDrawerPolicyTest 8`、`BrowserDownloadTransportTest 14`、`KiyoriShellStateTest 32`、`KiyoriSettingsPagesTest 7`，合计 `80/80`，零失败、零错误
- `python -B ci/script/check_formal_readiness.py --repository . --require-main` 通过；`:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 1m 1s`，`230` 个任务零失败，其中 `26` 个执行、`204` 个为 up-to-date
- Debug APK：`2026-07-27 05:39:49 +08:00`，`449493010` 字节，SHA-256 `C7E701E83B9020701B2AB7E7569F96F86BD82C0DA99E92D9965EBA22BED4A93A`，包名 `com.kiyori`，版本 `45 / 0.1.0`，`minSdk 26`、`targetSdk 34`、`compileSdk 36`，Android Debug V2 签名和 `zipalign -c -P 16 -v 4` 通过
- 抽屉视觉与拖动、菜单周围点击关闭、不同格式长按矩阵、输入法遮挡、Android `DownloadManager`、SAF 文件移动与权限、真实 M3U8 媒体 remux 和 APK 自动清理仍需目标 Android 设备验收，当前切片保持 `verification_pending`

### 2026-07-27 统一入口与下载器复核增量

- 负一屏“下载”不再打开旧全屏页面，而是把 `KiyoriShellState` 的共享下载抽屉状态置为可见；系统 Back、切换主页面、打开设置子页和抽屉拖动关闭都归一到同一可见状态
- `BrowserDownloadManager.taskSnapshots` 继续是共享抽屉的唯一数据输入；Shell 宿主只构造 UI 投影和动作回调，不持久化第二份列表、排序、分类或批量状态
- 删除无消费者的 `deleteFileByDefault` 设置字段、setter 与 preference key 代码。删除弹窗继续要求用户本次明确选择“只删记录”或“记录和文件”，不把 UI 决策写入下载设置
- SAF 自定义目录被替换、恢复默认或因启用自动公开转存而清除时，设置 owner 释放不再使用的持久化 URI 权限；任务记录仍持有实际文件所需的 content URI 访问语义
- 目录选择读取失败或完成文件移动失败时，刚取得的 SAF tree 权限也按同一 owner 判定释放；当前设置或其他任务仍引用该目录时不释放
- 下载传输的 HEAD 与精确 `0-0` Range 元数据探测进入与正文、分段和 M3U8 资源相同的五次有界重试；测试分别覆盖瞬时 HEAD 失败和探测最终失败次数
- SAF `ContentResolver.delete()` 只有删除行数大于零才视为成功。移动或转存的源删除返回零行时必须回滚新副本并保留原任务位置，不能误报完成并遗留源文件
- 活动/暂停任务的“批量取消”使用独立批量动作，只允许选择 `canCancel` 任务并调用 manager cancel；它不再复用批量删除确认，暂停任务卡的内联第二动作也统一为“取消”
- 验收必须证明 `KiyoriDownloadCenterPage`、`DOWNLOAD_CENTER`、旧 `BrowserDownloadTaskCard`、旧三筛选投影和 `deleteFileByDefault` 在项目源码与测试中零引用

### 2026-07-27 软件首页触摸回归修复

- 目标设备反馈软件首页无法滑动且全部按钮无响应。根因是 Shell 始终以 `zIndex(30)` 组合 `KiyoriDownloadDrawerHost`；即使抽屉状态为隐藏，内部仍保留 `fillMaxSize()` 的透明遮罩与点击树，位于软件首页 Pager 和按钮之上
- 宿主现在只在抽屉可见或退出动画尚未完成时组合。完全隐藏后从组合树移除，不创建下载 manager、任务订阅、透明遮罩、抽屉内容或点击节点；关闭过程继续由原 `WebSessionBrowserBottomDrawer` 完成弹簧退出动画
- `KiyoriShellStateTest` 新增显示、退出动画保留、完全隐藏移除三态策略覆盖，当前 `33/33` 通过；`:app:compileDebugKotlin`、Formal readiness 和 `git diff --check` 通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 37s`，`230` 个任务零失败，其中 `25` 个执行、`205` 个为 up-to-date
- Debug APK：`2026-07-27 11:27:54 +08:00`，`449493010` 字节，SHA-256 `A444F8D9BDAFAC45603620AE43AFC714881A68FF54EB59ECC31AE7BFDD398A3C`，包名 `com.kiyori`，版本 `45 / 0.1.0`，`minSdk 26`、`targetSdk 34`、`compileSdk 36`，Android Debug V2 签名和 `zipalign -c -P 16 -v 4` 通过
- 自动检查证明隐藏态不再组合全屏命中树，但软件首页实际滑动、搜索框、天气、窗口计数、AI 快捷动作和底部五入口仍需使用该 APK 在目标设备复测

### 2026-07-27 负一屏下载入口接线修复

- 负一屏下载卡已经调用 `openDownloadDrawer()`，但 Shell 又把抽屉显示与 `aiHostIsRoot` 绑定，并在后台 AI route 不是 `Screen.AiChat` 时立即关闭抽屉；由于 AI host 会在软件首页和负一屏后方保留内部路由，这个错误耦合会让点击表现为无响应
- 删除 AI 非根路由自动关闭下载抽屉的副作用；共享下载抽屉现在只由 `KiyoriShellState.isDownloadDrawerOpen` 控制，不受后台 AI route 深度影响
- 负一屏点击、抽屉关闭和打开下载设置都改用 `latestState` 与 `latestOnStateChange`，避免 Pager 页面持有旧 Shell 快照；即使 AI host 不是根页面，系统 Back 也会优先关闭已经打开的下载抽屉
- `KiyoriShellStateTest` 新增 AI 根路由与非根路由下的显示合同及 Back 优先级，当前 `34/34` 通过；`:app:compileDebugKotlin`、Formal readiness 和 `git diff --check` 通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 42s`，`230` 个任务零失败，其中 `25` 个执行、`205` 个为 up-to-date
- Debug APK：`2026-07-27 11:47:02 +08:00`，`449493010` 字节，SHA-256 `44381497BF14D1861F609F47328D51BF3E778B98E7E54176AA010ADD7B58E22E`，包名 `com.kiyori`，版本 `45 / 0.1.0`，`minSdk 26`、`targetSdk 34`、`compileSdk 36`，Android Debug V2 签名和 `zipalign -c -P 16 -v 4` 通过
- 自动验证证明入口状态不再受 AI route 影响；负一屏下载卡实际点击、抽屉进入动画和 Back 关闭仍需使用该 APK 在目标设备复测

### 2026-07-27 负一屏下载状态 owner 修复

- 目标设备继续反馈负一屏下载卡点击无响应。进一步追踪发现 `KiyoriAppShell` 已提交 `isDownloadDrawerOpen = true`，但 `OperitApp` 仍用多组 `rememberSaveable` 字段重建 Shell，并且构造与回写都遗漏下载抽屉字段；新状态在顶层桥接中立即丢失
- `OperitApp` 现在直接持有单一可保存的 `KiyoriShellState`。`KiyoriShellStateSaver` 按确定顺序保存主目的地、软件首页页码、child、child Back target、AI 抽屉、下载抽屉和浏览器返回目标，不再维护容易漏字段的并行状态桥接
- `KiyoriShellStateTest` 新增完整保存恢复合同并通过 `35/35`；Formal readiness、旧拆分式 Shell 保存变量零匹配与 `git diff --check` 通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 56s`，`230` 个任务零失败，其中 `25` 个执行、`205` 个为 up-to-date
- Debug APK：`2026-07-27 12:44:52 +08:00`，`449493010` 字节，SHA-256 `B658EEEB9E854011F58C475321279269E80443C2E3E113E8451E08B75B9269F9`，包名 `com.kiyori`，版本 `45 / 0.1.0`，`minSdk 26`、`targetSdk 34`、`compileSdk 36`，Android Debug V2 签名和 `zipalign -c -P 16 -v 4` 通过
- 自动验证证明下载抽屉可见状态已经跨过应用根状态 owner；负一屏实际点击、抽屉进入动画和系统 Back 关闭仍需在目标设备安装该 APK 后复测

### 2026-07-27 下载抽屉内容视口自适应

- 共享 `WebSessionBrowserBottomDrawer` 过去始终按全展开高度测量内容，仅通过整体向下位移形成半展开效果；位于屏幕下方的区域仍被 `LazyColumn` 计入可见高度，导致半展开状态的末尾条目无法滚入屏幕
- 抽屉 Surface 继续保持全高以维持既有位移和弹簧动画，内容容器改为按实时露出高度减去 `28dp` 拖动柄计算视口；半展开、完全展开及拖动过程都向子内容发布真实高度，当前下载抽屉和以后复用该宿主的抽屉共用同一自适应合同
- 下载页签继续由各自的 `LazyColumn` 持有垂直滚动；高度变化只触发重新测量，不引入第二滚动 owner，也不改变页签、排序、批量操作或下载任务状态
- “已下载”为空时保留纯背景，不再显示“还没有已下载内容”；“下载中”为空时继续显示“当前没有下载任务”
- `WebSessionBrowserChromeLayoutTest` 覆盖展开、半展开和隐藏三种内容视口高度，当前 `7/7` 通过；目标测试任务同时完成 `:app:compileDebugKotlin`，Formal readiness、`git diff --check` 和主源码旧已下载空态文案零匹配均通过
- 收尾再次执行 `:app:assembleDebug --no-daemon --console=plain`，结果为 `BUILD SUCCESSFUL in 19s`，`231` 个任务零失败，其中 `22` 个执行、`209` 个为 up-to-date
- Debug APK：`2026-07-27 13:55:15 +08:00`，`449493090` 字节，SHA-256 `DDC7E85641ADF1F7CE208760513152C8067C93452FDF7C757A0E4DD19CB8DCC5`，包名 `com.kiyori`，版本 `45 / 0.1.0`，`minSdk 26`、`targetSdk 34`、`compileSdk 36`，Android Debug V2 签名和 `zipalign -c -P 16 -v 4` 通过
- 真机仍需验证半展开和完全展开两种稳定状态下首尾条目都能完整滚入视口，并观察拖动过程中高度连续变化是否符合目标视觉

### 2026-07-27 悬浮下载页 Activity Result owner 修正

- 悬浮浏览器下载页组合时会无条件创建 `rememberLauncherForActivityResult(OpenDocumentTree)`，但 `TYPE_APPLICATION_OVERLAY` 的 `ComposeView` 没有 `LocalActivityResultRegistryOwner`，因此下载抽屉在显示阶段直接崩溃
- 目录选择改由独立透明 `WebSessionDirectoryPickerActivity` 持有真实 `ComponentActivity` registry；一次性协调器只返回本次 tree URI，原有持久化授权、移动任务和未使用授权释放继续由下载 owner 执行
- 应用内与悬浮下载抽屉共用同一目录选择入口，不依赖当前前台 Activity，也不改变普通文件和 M3U8 离线包的长按动作矩阵
- `WebSessionDirectoryPickerCoordinatorTest 2/2` 与 `BrowserOverlayWindowPolicyTest 3/3` 通过；定向任务完成 Debug Kotlin 和 Manifest 编译且没有新增编译警告
- 共享下载页旧 Compose launcher/owner 依赖为零引用，合并 Manifest 与 APK 均包含透明目录选择 Activity；Formal readiness、`git diff --check` 和禁用降级语义扫描通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m`；Debug APK 时间 `2026-07-27 14:13:14 +08:00`，`449493090` 字节，SHA-256 `34136CDBA8958FA702E0FEA14476DE4FED226D93B5F224261F618271E7D3BA66`，V2 签名及 16 KB ZIP 对齐通过
- 目标设备仍需验证悬浮抽屉可以打开、取消及完成系统 SAF 目录选择

## 历史记录：内部阶段 2 实现

以下内容记录旧全屏中心阶段的本地证据，已经被 2026-07-27 统一入口与下载器复核增量替代，不再描述当前 UI 合同。

- Settings Home“文件下载器”直接进入 Download Settings；负一屏“下载”进入 Shell Download Center 并显示 manager 的真实任务数，浏览器菜单“下载”进入共享紧凑下载抽屉
- Download Center 按手机单列、`>=720dp` 双列展示任务卡，并复用“下载中、已完成、失败”纯过滤合同
- 中心设置按钮进入 nested Download Settings；Back 先返回中心，再返回原 Settings Home 或负一屏 owner
- 浏览器紧凑抽屉和全屏中心复用同一任务卡；删除统一先确认，复选框初值由 settings store 持有
- 删除记录始终清理该记录拥有的 `.part` 文件；仅在用户勾选时删除已完成目标文件
- Download Settings 完整保留旧版 `5/3/3/1` 十二行。当前真实接入下载目录、`1..4` 同时任务数和 `1/2/4/8` 普通 HTTP Range 线程数；其余导航行为为空页、勾选行为不可交互
- `BrowserDownloadPolicyTest` 7 项、`KiyoriSettingsPagesTest` 6 项、`KiyoriShellStateTest` 31 项，共 44/44 通过；正式开发门禁与 `git diff --check` 通过
- Debug APK：`2026-07-26 14:00:15 +08:00`，`449485670` 字节，SHA-256 `3F91ADED64FCAB24EEFBA773A332E3B46E7E00A5441AE71E8283AC43B2B2F064`，`com.kiyori`，`45 / 0.1.0`，Android Debug v2 签名和 `zipalign -c -P 16 4` 通过
- 真机视觉、宽屏布局、系统下载目录 Intent、删除文件与多任务实时 UI 仍为 `verification_pending`

## 自问自答

### 为什么不直接移植旧 Kiyori 下载器？

当前 manager 已经与 WebSession、AI 结果和下载目录深度接线。整套替换会制造第二个任务数据库和状态 owner。本轮应补齐队列、设置和 UI，而不是重建下载内核。

### 为什么恢复后不自动继续？

进程恢复时网络、计费和目标文件状态可能已经变化。明确恢复可避免未经用户动作重新产生网络副作用。

### 为什么系统下载不显示在 Kiyori 下载抽屉？

Android `DownloadManager` 已经持有系统任务的 ID、进度、通知、失败和生命周期。把它复制进 Kiyori JSON 会产生无法原子同步的第二 owner，因此系统模式明确只在系统下载界面管理。

### 为什么 `blob:` 和 `data:` 不跟随系统下载器设置？

这两类 URL 是当前 WebView 内部生成的数据，不是 Android `DownloadManager` 接受的网络资源。现有 inline payload 路径能保存实际字节，因此继续由内置 owner 处理，没有在失败后切换下载器。

## 预计文件

- `BrowserDownloadSupport.kt`
- `BrowserDownloadM3u8Policy.kt`
- `WebSessionBrowserHostState.kt`
- `WebSessionDownloadDrawer.kt`
- `KiyoriDownloadDrawerHost.kt`、设置 store 和页面
- Shell/Settings/Browser callbacks
- 队列、设置、状态恢复和 UI contract 测试
- `README.md`、`CONTEXT.md`

## 验收

- 并发任务数和分段线程设置真实约束 manager
- 多任务暂停、恢复、取消、失败和完成时队列顺序正确
- 浏览器与负一屏的共享下载抽屉显示同一任务和实时进度
- 打开文件、位置和删除动作保持现有安全确认
- Debug APK 与本地门禁通过；提交、推送和远端 SHA 仅在用户另行授权时执行
