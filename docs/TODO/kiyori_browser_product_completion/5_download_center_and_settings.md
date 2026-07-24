# 下载中心与下载设置

## 旧实现

`BrowserDownloadManager` 已支持 HTTP 分段、暂停、恢复、取消、重试、记录删除、状态恢复、速度、打开文件和打开位置。当前每个任务立即启动，分段线程固定为 4，页面只存在浏览器子抽屉，没有全屏下载中心和真实设置页。

## Runtime 改造

- 保留现有 JSON task 格式并为新增设置建立独立版本化 preference store
- 增加全局任务调度队列，运行任务数不得超过 `maxConcurrentTasks`
- 分段计划使用 `segmentThreadCount`，只对服务端支持 Range 且文件大小满足条件的任务生效
- 暂停、取消、完成和失败后立即调度下一项 QUEUED 任务
- 应用恢复时把中断的活动任务规范化为可恢复状态，再由用户明确恢复；不自动发起网络请求
- 所有异常写入 `AppLogger` 和任务可见错误，不吞掉失败

## 下载设置

首期设置全部直接约束当前 manager：

- 同时下载任务数：1 至 4
- 单任务分段线程数：1、2、4、8
- 下载目录：只读显示 `Download/Kiyori/browser/downloads/`，提供打开目录动作
- 删除记录时是否同时删除文件：只作为删除对话框默认选项，不绕过确认

不移植 M3U8 自动合并、自定义下载引擎、HTTP 协议切换、APK 自动清理等当前 manager 不具备的开关。

## 全屏下载中心

- 顶部包含返回、标题、进行中数量和设置入口
- selector 为“下载中”“已完成”“失败”，复用现有 filter 语义
- 任务卡显示文件名、进度、速度、大小、状态、保存位置和上下文动作
- 宽屏使用双列卡片；手机保持单列和足够的触摸面积
- 支持暂停、继续、取消、重试、打开文件、打开位置、删除记录和删除文件
- 批量操作只在有明确目标集合时显示，删除文件必须二次确认

浏览器抽屉保留紧凑下载视图；Settings Home 和负一屏进入全屏下载中心。三处读取同一个 `BrowserDownloadManager` snapshot 和事件流。

## 自问自答

### 为什么不直接移植旧 Kiyori 下载器？

当前 manager 已经与 WebSession、AI 结果和下载目录深度接线。整套替换会制造第二个任务数据库和状态 owner。本轮应补齐队列、设置和 UI，而不是重建下载内核。

### 为什么恢复后不自动继续？

进程恢复时网络、计费和目标文件状态可能已经变化。明确恢复可避免未经用户动作重新产生网络副作用。

## 预计文件

- `BrowserDownloadSupport.kt`
- `WebSessionBrowserHostState.kt`
- `WebSessionDownloadSheet.kt`
- 新的 Download Center、设置 store 和页面
- Shell/Settings/Browser callbacks
- 队列、设置、状态恢复和 UI contract 测试
- `README.md`、`CONTEXT.md`

## 验收

- 并发任务数和分段线程设置真实约束 manager
- 多任务暂停、恢复、取消、失败和完成时队列顺序正确
- 抽屉与全屏下载中心显示同一任务和实时进度
- 打开文件、位置和删除动作保持现有安全确认
- Debug APK、提交、推送和远端 SHA 门禁通过
