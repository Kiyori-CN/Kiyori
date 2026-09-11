---
status: in_progress
---

# AI 体验页面与弹层定位清单

本表是 2026-09-09 根据源码声明形成的初始定位表，服务于[专项计划](index.md)。包含页面、面板、菜单和弹窗所在文件；它不代表逐项人工审查已完成。每个文件的行内 AlertDialog、Dialog、Popup 和底部面板继续在对应阶段检查。动态 ToolPkg 页面还需从实际注册目录和预置清单追踪，不能仅靠静态 Screen 名称宣称覆盖完整。

状态“待查”表示仅定位了源文件与声明；“已审”必须在阶段记录中附问题或无变更结论以及证据。所有现场验证目前为 `verification_pending`。

2026-09-11 入口补充：AI 顶栏从左到右为网页浏览器、文件管理器、终端、对话详情；文件入口与
悬浮球使用同一紫色轮廓 Folder。浏览器和文件管理器的 AI 入口移至各自固定三行的二级工具箱，
沿用 Shell/WebSession 会话；实现与验证记录见[文件管理 M3l](../mt_file_manager_replica/index.md)。

## 当前续审矩阵

观察日期：2026-09-09，源码基线为父 `4b1be9868`、terminal `53a8ea5b`。下表是本轮活动矩阵，后续按实际回调继续拆到叶子；尚未展开的分组明确作为调查队列，不能计为完成。下方原文件表是定位索引，旧增量表是历史证据，均不覆盖本表的当前验收状态。

为保持可读性，关联字段合并为单元格：入口/退出分别给出来源和返回；边界包含读写、异步与生命周期；场景分别写用户与 Agent；验证分列测试、构建、现场。每行回滚点 `R0` 指本节双仓基线上的精确文件差异，禁止全树重置。`A0` 表示本轮只有源码定位或阅读，尚未测试、尚未构建；`V0` 表示现场 `verification_pending`，原因是本轮未授权设备或真实服务操作。它们是各行完整字段的共同取值，不是通过标记。

| 阶段/叶子或调查分组 | 页面/组件与涉及文件 | 入口 / 退出 | 真实状态 owner | 读写/异步/生命周期边界 | 缺陷分类与触发条件 | 用户场景 / Agent场景 | 优先级/依赖 | 风险/回滚点 | 实施状态 | 测试状态 | 构建状态 | 现场状态及原因 | 下一步 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P2 消息正文保存/模式切换/关闭 | MessageEditor、ChatScreenContent、ChatViewModel.reviseConversationAuditMessage | 消息菜单 → 编辑/标签 / 保存或放弃确认 | 原编辑值与审计仓储 | chat/timestamp/variant、原文校验、组合scope | 已关闭本地缺陷：等待保存、保留空白、同步写入输入值 | 修改消息 / 工具XML正文 | 高；原审计链 | 原文和草稿丢失/R0 | 已实施本地验证 | 新增4项+审计4项通过，0失败错误跳过 | E2A8CDE5，14:17:07通过 | V0：IME/窄屏/Room | 编辑重发仍待审 |
| P1 插件异常/排队取消 | ChatInputHookRegistry、PendingQueueSubmission、AIChatScreen、PendingMessageQueueStore | 发送或队列发送 / 返回输入 | 原输入与队列owner | hook挂起、移出队列、取消/派发边界 | 已实施：异常阻断、未派发取消恢复、删除chat不复活、未知提交不恢复 | 插件检查草稿 / 插件提交回调 | 最高；发送链 | 意外发送/队列丢失/R0 | 已实施本地验证 | 13通过，0失败错误跳过 | 9F9E7B1A，14:33:37通过 | V0：真实插件 | 继续共享owner及历史操作 |
| P3 历史聊天删除/确认 | ChatHistorySelector、ChatViewModel、ChatHistoryDelegate、ChatHistoryManager、ChatDeletionCompletion | 历史滑动/菜单 → 删除确认 / 成功或错误确认 | 原服务/Room/队列owner | chatId、服务持有提交、实体删除/后续清理边界 | 已证实：提前隐藏关闭；清理失败混为未删；旧偏好读取可能清新选择 | 删除历史 / 后台生成停止与审计清理 | 高；原历史锁/审计 | 部分完成及重复操作/R0 | 已实施本地验证 | 10通过，0失败错误跳过 | 4A207572，14:43:02通过 | V0：Room/Keystore/实际切chat | 验证后继续分组/重排 |
| P3 新建分组/名称/绑定/取消 | ChatHistorySelector、ChatCreationCompletion、ChatViewModel、ChatHistoryDelegate、ChatHistoryManager | 新建分组 → 表单 / 创建结果或放弃 | 原Room/服务/选择collector | 打开时绑定、创建与选择分离、DataStore原值比较 | 已证实：异步创建立即丢草稿；选择失败可能重复创建 | 新建历史分组 / 并发切chat | 高；原审计创建事务 | 重复创建/抢选择/R0 | 已实施本地验证 | 最终18通过，0失败错误跳过 | CBCF4619，15:15:55通过 | V0：DataStore/Room/IME | 核对构建后继续加载与重排 |
| P3 选择/加载/分页/错误/重试/关闭 | ChatHistoryDelegate、CurrentChatWindowController、ChatViewModel、ChatScreenContent、ChatArea | 历史选择/新建/定位/翻页 → 安全错误条 / 重载或关闭 | 原服务/Room/窗口投影 | collectLatest、chatId/代次、原子读取快照与重试、实时更新撤销读取 | 已实施：旧加载发布及取消、空选择清理、stream竞争、迟到关闭/重试隔离 | 快速切chat与重载 / 后台生成与定位 | 最高；历史owner | 加载错报/旧任务发布/R0 | 已实施本地验证 | 最终14通过，0失败/错误/跳过 | E74C4B68，15:58:04通过 | V0：实际并发/Room/大字体/TalkBack | 选择提交/历史重排继续反审；窗口真实并发/视觉待现场 |
| P3/P4 选择提交/偏好读取/返回主应用 | ChatHistoryDelegate、CurrentChatSelectionObserver、ChatRuntimeHolder、历史入口与浮窗标题栏 | 历史选择/浮窗返回 → 等待结果 / 失败保留与手动重载 | 原DataStore/服务/runtime | 选择互斥、原值比较、错误身份、来源协程资格与取消读取 | 已实施：读取失败不清选择、失败不关闭入口、返回等待持久化 | 偏好I/O失败/连续切换/浮窗返回 | 最高；历史owner | 提交后观察未知/R0 | 本批本地验证通过；角色同步仍待修 | 69129：6项观察+14项窗口全部通过 | 15997通过，17:01:19，35B28A8F | V0：真实DataStore故障/OEM窗口 | 接角色多偏好写入与编辑重发/回滚 |

| P3 拖拽/上移/下移/跨组绑定 | ChatHistorySelector、ChatOrderMove、ChatViewModel、ChatHistoryDelegate、ChatHistoryManager、ChatDao | 历史拖动或条目菜单 → 移动 / 成功或错误重试 | 原服务/Room Flow | 单项/锚点快照、同事务顺序与绑定、服务持有 | 已实施：过滤子集整表回写、拆分绑定和排序、debounce异常无终态 | 筛选/折叠后移动 / 并发元数据与删除 | 最高；原历史owner | 隐藏项消失/部分保存/旧字段覆盖/R0 | 已实施本地验证 | 12通过，0失败/错误/跳过 | C5D2FCEA，16:12:05通过 | V0：Room并发/连续拖动/大字体/TalkBack | 继续选择持久化和消息重发；拖动及真实并发待现场 |
| P3 分组改名/删除范围与结果 | ChatGroupTarget/Scope、HistoryListItem.Header、ChatHistorySelector、ChatHistoryDelegate、ChatHistoryManager | 分组长按 → 改名/两种删除 / 返回历史 | 原服务/Room | 分组名+绑定分类、批量写入、停止活动chat、异步结果 | 已实施：显式scope、默认角色排除群组；等待结果；成员/锁定事务比较 | 管理群组历史 / 后台生成与删除并发 | 最高；历史删除等待链 | 错分类修改/误删/R0 | 已实施本地验证 | 17通过，0失败错误跳过 | 31F22B56，15:00:26通过 | V0：Room/实际并发 | 核对结果后继续创建/重排 |
| P3 历史标题/绑定编辑 | ChatHistorySelector、ChatMetadataEdit、ChatHistoryManager、ChatHistoryDelegate、ChatViewModel | 历史长按/滑动 → 编辑 / 保存或取消、放弃确认 | 原ChatHistoryManager及Room Flow | chatId、原值快照、同chat锁与事务、服务持有提交 | 已关闭本地缺陷：原双异步调用立即关闭；失败与并发修改边界已修复 | 编辑标题与角色 / 自动标题或绑定写入并发 | 高；既有DAO/Room | Room现场故障仍待验/R0 | 已实施本地验证 | 7通过/0失败错误跳过 | E935E247，14:07:19通过 | V0：IME/大字体/数据库设备 | 继续分组和删除；现场单独验收 |
| P6a 绑定/解绑 | WorkspaceSetup、ChatHistoryDelegate、ChatDao | 顶栏工作区 → 目录选择 / 返回聊天 | ChatViewModel → 服务 → ChatHistoryManager | async.await 后发布；SQL 比较原路径/环境 | 已有等待实现；原值获取时机属调查线索 | 绑定已有目录 / 工具更改绑定 | 高；目录provider | 迟到绑定及冲突/R0 | 部分源码核实 | A0 | A0 | V0：SAF/SSH | 追踪点击快照及失败草稿 |
| P6a 创建/模板/重新绑定 | WorkspaceSetup、WorkspaceUtils、WorkspaceCreation | 未绑定工作区 → 模板确认 / 成功或取消 | 现有工作区创建服务 | staging发布与绑定非共同事务 | 设计约束；模板成功但绑定失败 | 重用旧目录 / 模板工具 | 高；绑定链 | 部分完成/R0 | 调查队列 | A0 | A0 | V0：文件provider | 复核每个确认回调 |
| P6a 文件/书签/管理模式 | FileManager、WorkspaceEditorState | 文件面板 → 新建/改名/删除/书签 / 关闭面板 | 原文件provider与编辑状态 | 路径/环境、dirty、异步持久化 | 调查线索；失败/并发编辑/移除被引用书签 | 管理文件 / 文件工具刷新 | 高；绑定 | 文件丢失/R0 | 调查队列 | A0 | A0 | V0：SAF | 逐个操作和错误路径 |
| P6a 回滚/恢复 | WorkspaceBackupManager、ChatHistoryDelegate | 消息回滚确认 / 聊天 | 历史锁及备份owner | 先文件恢复、SQL、后清理清单 | 设计约束：无跨文件/SQL事务 | 回滚重发 / 工具文件改动 | 高；消息身份 | 部分恢复/R0 | 调查队列 | A0 | A0 | V0：文件I/O | 注入中间失败并核对反馈 |
| P6a 命令/预览/导出 | WorkspaceManager、ExportDialogs | 工作区菜单 / 结果关闭 | 现有终端、预览、导出owner | chat/环境/commandId与取消清理 | 调查线索；切chat/配置/关闭 | 预览与打包 / 执行命令 | 高；provider | 错目标执行/R0 | 调查队列 | A0 | A0 | V0：命令/存储 | 核对任务身份及终态 |
| P6a 终端设置/SSH/源 | TerminalScreen、SettingsScreen、SSHConfigScreen | 终端设置 → 编辑/环境 / Back | TerminalManager与配置manager | 保存等待、字段保留、setup路由 | 上轮已有修复，待源码反审 | 配置连接 / 终端调用 | 高；子模块规则 | 配置/锁顺序/R0 | 调查队列 | A0 | A0 | V0：真实SSH | 核对持久化/日志/初始化 |
| P6a 会话/键盘 | TerminalHome、CanvasTerminalCompose、VirtualKeyboardCustomizationDialog | 会话/键盘菜单 / 终端 | TerminalManager、输入队列 | 当前session、原始按键与销毁 | 调查线索；切session时输入/关闭 | 粘贴选择 / 并发命令 | 高；子模块 | 错会话/R0 | 调查队列 | A0 | A0 | V0：设备键盘 | 逐按钮复核 |
| P6b 时间线/payload/Raw | ConversationDetailsScreen | 顶栏详情 → Tab/展开/复制 / 关闭 | ConversationAuditRepository与详情投影 | chat/eventId、分页、解密取消 | 调查线索；切chat/失败/敏感文本 | 诊断详情 / Provider审计 | 高；审计契约 | 错内容/泄密/R0 | 入口与契约核实 | A0 | A0 | V0：Keystore/TalkBack | 逐Tab与payload链检查 |
| P6b 修订/批注/导出/统计 | ConversationDetailsScreen、ConversationAuditExporter | 详情动作 → 表单 / 结果或取消 | 原审计owner | 原文比较、staging、实际写入结果 | 设计约束；发布文件后审计失败 | 保存诊断 / 读取审计 | 高；时间线 | 部分完成/R0 | 调查队列 | A0 | A0 | V0：实际存储 | 检查冲突/取消/敏感脱敏 |
| P6c registry启用/禁用/删除 | PackageManager、ToolPkgManager | 扩展详情或市场动作 / 列表 | 唯一PackageManager/ToolPkgManager | initLock、引擎线程、关联偏好 | 已证实：disablePackage锁外读改写关联名单；并发可丢更新 | 快速启停 / 工具调用期间卸载 | 最高；引擎释放设计 | 死锁/旧清理伤新实例/R0 | 待实现 | A0 | A0 | V0：QuickJS设备 | 先拆状态发布与资源释放 |
| P6c Skill导入/详情/删除 | SkillConfigScreen、SkillManager | Skill标签 → GitHub/ZIP/手动/详情 / 关闭 | SkillManager mutationLock | staging、单目录名、no-follow | 调查线索；取消/重名/刷新失败 | 安装技能 / Skill调用 | 高；registry | 部分文件发布/R0 | 调查队列 | A0 | A0 | V0：下载/SAF | 复核所有导入结果及弹层 |
| P6c MCP配置/部署/导入 | MCPConfigScreen、MCPDeployViewModel、MCPRepository | MCP标签 → 编辑/部署/ZIP / 关闭 | MCPLocalServer/MCPManager与ViewModel | 配置锁、pluginId/代次、观察scope | 调查线索；后台草稿/部署取消 | 编辑服务 / MCP调用 | 高；终端/配置 | 部分部署/R0 | 调查队列 | A0 | A0 | V0：服务/SSH | 配置状态文件与后台确认 |
| P6c 脚本/ToolPkg详情与执行 | PackageDetailsDialog、ScriptExecutionDialog | 扩展 → 参数/工具 / 关闭 | PackageManager与执行owner | 包身份、参数快照、任务释放 | 调查线索；流式回传/切子包 | 测试脚本 / 工具执行 | 高；registry | 重复副作用/R0 | 调查队列 | A0 | A0 | V0：脚本运行 | 核对取消及失败保留 |
| P6c 市场列表/详情/账号 | UnifiedMarket各Screen/ViewModel | 扩展市场 → 作者/通知/管理/评论 / Back | 原市场ViewModel与认证缓存 | 账号/条目/页代次及可见性 | 调查线索；切账号/旧请求 | 浏览安装 / 扩展更新 | 高；registry/缓存 | 错账号结果/R0 | 调查队列 | A0 | A0 | V0：真实市场 | 按页拆筛选、确认、重试 |
| P6c 发布/登记恢复 | ArtifactPublishScreen、RepoMarketPublishScreen | 市场发布 → 表单/确认 / Back | 原发布ViewModel | 上传后未知登记、跨进程边界 | 设计约束：当前恢复只在ViewModel | 发布制品 / 不执行真实发布 | 高；持久化设计 | 重复发布/R0 | 调查队列 | A0 | A0 | V0：真实发布未授权 | 明确跨进程恢复合同 |
| P6d SQL单元格/滚动 | SqlViewerScreen/ViewModel | 工具箱SQL → 查询/单元格 / Back | SqlViewerViewModel | 查询快照、Canvas语义、分页 | 调查线索；复杂SQL/长值/TalkBack | 查看数据库 / SQL工具 | 高；SQL查询计划 | 不可读结果/R0 | 调查队列 | A0 | A0 | V0：数据库设备 | 补单元格与语义审查 |
| P6d Shell/日志/ToolTester | 对应Screen/ViewModel | 工具箱 → 运行/确认/结果 / Back | 各原ViewModel | 执行占位、日志队列、批次停止 | 调查线索；隐藏页面/清理/取消 | 诊断 / 工具批量 | 高；日志/执行owner | 外部副作用/R0 | 调查队列 | A0 | A0 | V0：Shell/SAF | 反审首批剩余边界 |
| P6d TTS/STT | TextToSpeechScreen、SpeechToTextScreen | 工具箱/语音设置 → 测试 / Back | SpeechServiceFactory/VoiceServiceFactory | 录音/播放lease、权限、离页 | 调查线索；共享资源交接 | 语音测试 / 语音工具 | 高；P5 | 抢资源/R0 | 调查队列 | A0 | A0 | V0：真实语音 | 逐动作/错误/取消 |
| P6d 权限/默认助手/进程 | AppPermissionsScreen、DefaultAssistantGuideScreen、ProcessLimitRemoverScreen | 工具箱 → 系统设置/确认 / 恢复 | 原权限快照与系统owner | Activity结果、实际权限与命令范围 | 调查线索；拒绝/无可用Activity | 授权 / 权限工具 | 高；系统入口 | 错报授权/R0 | 调查队列 | A0 | A0 | V0：OEM/系统 | 区分说明与执行状态 |
| P6d UI调试/自动化/Token | UIDebuggerScreen、AutoGlm两页、TokenConfigWebViewScreen | 工具箱 → 子控件 / Back | 原调试/自动化/配置owner | 后台可见性、执行及WebView释放 | 调查线索；离页/失败/重复运行 | 调试 / 自动化 | 高；原运行时 | 后台执行/R0 | 调查队列 | A0 | A0 | V0：设备/网络 | 按16项目录逐页拆分 |
| P6d FFmpeg/HTML | FFmpegToolboxScreen、HtmlPackagerScreen | 工具箱 → 文件/参数/导出 / Back | 原FFmpeg/打包owner | provider、输入快照、取消清理 | 调查线索；长任务/失败 | 媒体与打包 / 文件工具 | 高；文件权限 | 部分产物/R0 | 调查队列 | A0 | A0 | V0：实际文件 | 核对入口可达子动作 |
| P6e 列表/新建/模板/批删 | WorkflowListScreen、WorkflowViewModel | 抽屉工作流 → 新建/模板/选择 / Back | WorkflowViewModel/WorkflowRepository | 列表代次已有；写入互斥待审 | 调查线索；快速重复/离页迟到导航 | 创建工作流 / 管理工具 | 高；仓储 | 重复创建/R0 | 部分源码核实 | A0 | A0 | V0：实际调度 | 逐确认等待与草稿 |
| P6e 详情/日志读取 | WorkflowDetailScreen、WorkflowViewModel.loadWorkflow | 列表条目 → 日志 / Back | 同一WorkflowViewModel | 当前详情与最新执行记录异步发布 | 已证实：loadWorkflow无目标代次，失败清日志且无日志错误反馈 | 切换详情 / 后台执行刷新 | 高；列表 | 迟到覆盖/R0 | 待实现 | A0 | A0 | V0：实际执行 | 绑定workflowId/代次 |
| P6e 节点/连接/变量/触发器 | NodeDialog、ConnectionMenu、ScheduleConfigDialog | 编辑器 → 配置/删除 / 返回画布 | WorkflowViewModel/Repository | 节点/边ID、版本、整图读改写 | 调查线索；并发移动/编辑/非法连接 | 编辑图 / 工作流工具 | 最高；仓储 | 丢更新/非法执行/R0 | 调查队列 | A0 | A0 | V0：调度/触发器 | 拆类型校验和持久化 |
| P6e 运行/停止/导入导出 | WorkflowDetailScreen、WorkflowListScreen | 运行或文件动作 / 完成/取消 | 现有执行器与仓储 | workflowId、重复运行、外部结果 | 调查线索；取消/覆盖/页面退出 | 运行图 / 工作流调用 | 高；节点与执行器 | 重复副作用/R0 | 调查队列 | A0 | A0 | V0：真实工作流 | 核对实际支持功能和确认 |
| P6f 搜索/图谱/文件夹切换 | MemoryScreen、MemoryViewModel | 记忆库 → 搜索/文件夹 / 清空/Back | MemoryViewModel/MemoryRepository | 多链写入同一graph，查询变更不失效旧请求 | 已证实：loadMemoryGraph和search独立发布；快速切换可覆写 | 查询/浏览 / 记忆工具刷新 | 最高；图谱owner | 旧结果覆盖/R0 | 待实现 | A0 | A0 | V0：索引/设备 | 统一原owner内发布资格 |
| P6f 文件夹创建/改名/删除 | FolderNavigator、MemoryViewModel | 文件夹菜单 → 确认 / 导航 | MemoryRepository及profile owner | 路径/引用/索引刷新 | 调查线索；嵌套路径/失败 | 管理目录 / 写记忆 | 高；图谱发布 | 引用/索引漂移/R0 | 调查队列 | A0 | A0 | V0：ObjectBox设备 | 逐菜单等待与部分失败 |
| P6f 文档/编辑/链接/边/批删 | DocumentViewDialog、EditMemoryDialog、MemoryDialogs | 文档或图节点 → 弹层 / 关闭 | MemoryViewModel/Repository | memoryId/chunkId/edgeId、原值与离页 | 调查线索；编辑冲突/关闭迟到结果 | 编辑知识 / 记忆工具 | 高；仓储 | 丢草稿/孤儿链接/R0 | 调查队列 | A0 | A0 | V0：真实文档 | 逐保存删除与冲突 |
| P6f 搜索设置/模拟/测试/导入 | MemorySearchSettingsDialog、MemorySearchSimulationDialog、ToolTestDialog | 记忆工具栏 → 表单/结果 / 关闭 | 原搜索配置与MemoryRepository | 配置/向量索引/请求代次/文件输入 | 调查线索；失败/重复/服务变更 | 调优搜索 / 检索工具 | 高；搜索发布 | 索引状态不一致/R0 | 调查队列 | A0 | A0 | V0：SAF/向量服务 | 核对每层配置和结果 |
| P6g 模型/协议/参数/能力 | ModelConfigScreen、ModelApiSettingsSection、ModelParametersSection | AI设置 → 模型/选择器 / Back | ModelConfigManager/FunctionalConfigManager | configId、保存协调器、测试请求 | 设计约束：不改协议、不能默认替代非法值 | 配置模型 / 请求编译 | 高；模型能力owner | 误提交/凭据泄露/R0 | owner与入口核实 | A0 | A0 | V0：模型服务 | 逐字段/弹层/保存失败 |
| P6g 提示词/角色/群组/用户 | ModelPromptsSettingsScreen及CharacterCardDialog等 | AI设置 → 编辑/绑定/导入 / Back | CharacterCard/Group、PromptTag、ActivePrompt managers | 身份、导入、并发保存与草稿 | 调查线索；切目标/迟到导入 | 个性化 / Prompt消费 | 高；原配置owner | 错绑定/R0 | owner与入口核实 | A0 | A0 | V0：文件/设备 | 逐16个提示词弹层等 |
| P6g 外观/上下文/工具/语音 | ThemeSettings、ContextSummary、ToolPermission、SpeechServicesSettings | 来源保持设置 → 选择/编辑 / Back | 各既有偏好/语音owner | 持久化、共享语音、后台弹层 | 设计约束：固定全局主题，局部AI外观 | 设置 / 工具权限及上下文 | 高；P5/设置来源 | 错权限/共享资源/R0 | 调查队列 | A0 | A0 | V0：语音/视觉 | 按设置路由拆叶子 |
| P6g 历史/备份/恢复/统计 | ChatHistorySettings、ChatBackupSettings、BackupDialogs、TokenUsageStatistics | AI设置 → 选择/确认 / 结果 | ChatHistoryManager与原备份owner | 数据库/文件/SAF、冲突/取消/结果等待 | 调查线索；导入失败/切chat/重名 | 备份恢复 / 对话数据 | 最高；审计/存储 | 数据丢失/R0 | 调查队列 | A0 | A0 | V0：SAF/恢复设备 | 核对全部格式与部分完成 |

当前原生工具箱目录来自 `ScreenRouteRegistry.hostEntryDefinitions`：ToolTester、TextToSpeech、SpeechToText、AppPermissions、DefaultAssistantGuide、Terminal、UIDebugger、FFmpegToolbox、ShellExecutor、Logcat、SqlViewer、TokenConfig、ProcessLimitRemover、HtmlPackager、AutoGlmOneClick、AutoGlmTool，共16项。`AppRouteCatalog` 继续合并 PackageManager 提供的动态 ToolPkg；无注册示例不能仅凭类名加入“可达且已审”计数。新增可达性发现须更新本表。

## 对话主界面、输入与消息

| 文件 | 页面或容器声明 | 行内弹层调用数 | 状态 |
| --- | --- | --- | --- |
| [AttachmentViewerDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/attachments/AttachmentViewerDialog.kt) | `AttachmentViewerDialog` | 1 | 待查 |
| [AttachmentSelector.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/AttachmentSelector.kt) | `AttachmentSelectorPanel`、`AttachmentSelectorPopupPanel`、`PackageSelectorDialog` | 2 | 待查 |
| [CharacterSelectorPanel.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/CharacterSelectorPanel.kt) | `CharacterSelectorPanel` | 1 | 待查 |
| [ChatArea.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ChatArea.kt) | `MessageCopyPreviewBottomSheet` | 3 | 待查 |
| [ChatHistorySelector.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ChatHistorySelector.kt) | 行内弹层 | 9 | 待查 |
| [ChatScreenContent.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ChatScreenContent.kt) | `ChatScreenContent`、`ChatHistorySelectorPanel` | 1 | 待查 |
| [ChatScrollNavigator.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ChatScrollNavigator.kt) | `ChatMessageLocatorDialog`、`visibleLocatorContent` | 1 | 待查 |
| [ChatStatisticsSheet.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ChatStatisticsSheet.kt) | `ChatStatisticsSheet` | 0 | 待查 |
| [TokenInfoDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/config/TokenInfoDialog.kt) | `TokenInfoDialog` | 1 | 待查 |
| [ExportDialogs.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ExportDialogs.kt) | `ExportPlatformDialog`、`AndroidExportDialog`、`WindowsExportDialog`、`ExportProgressDialog`、`ExportCompleteDialog` | 5 | 待查 |
| [FullscreenInputDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/FullscreenInputDialog.kt) | `FullscreenInputDialog` | 1 | 待查 |
| [LazyLayoutItemContentFactory.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/lazy/LazyLayoutItemContentFactory.kt) | `getContent` | 0 | 待查 |
| [LinkPreviewDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/LinkPreviewDialog.kt) | `LinkPreviewDialog` | 1 | 待查 |
| [MemoryFolderSelectionDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/MemoryFolderSelectionDialog.kt) | `MemoryFolderSelectionDialog` | 1 | 待查 |
| [MessageEditor.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/MessageEditor.kt) | `parseMessageContentForEditor`、`MessageEditor`、`TagEditorDialog` | 3 | 待查 |
| [MessageInfoDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/MessageInfoDialog.kt) | `MessageInfoDialog` | 1 | 待查 |
| [CustomXmlRenderer.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/part/CustomXmlRenderer.kt) | `RenderXmlContent`、`renderSearchContent`、`renderThinkContent`、`renderHtmlContent` | 0 | 待查 |
| [DialogComponents.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/part/DialogComponents.kt) | `ContentDetailDialog` | 1 | 待查 |
| [ToolResultDisplay.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/part/ToolResultDisplay.kt) | `ToolResultDetailDialog` | 1 | 待查 |
| [ScrollToBottomButton.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ScrollToBottomButton.kt) | `ScrollToBottomButtonContent` | 0 | 待查 |
| [SharedFileTargetDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/SharedFileTargetDialog.kt) | `SharedFileTargetDialog` | 1 | 待查 |
| [ShareImagePreviewDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ShareImagePreviewDialog.kt) | `ShareImagePreviewDialog` | 1 | 待查 |
| [BubbleUserMessageComposable.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/bubble/BubbleUserMessageComposable.kt) | `parseMessageContent` | 1 | 待查 |
| [HiddenUserMessagePlaceholderContent.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/common/HiddenUserMessagePlaceholderContent.kt) | `HiddenUserMessagePlaceholderContent` | 0 | 待查 |
| [SummaryMessageComposable.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/cursor/SummaryMessageComposable.kt) | 行内弹层 | 2 | 待查 |
| [UserMessageComposable.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/cursor/UserMessageComposable.kt) | `parseMessageContent` | 1 | 待查 |
| [AgentChatInputSection.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/agent/AgentChatInputSection.kt) | `AgentModelSelectorPopup`、`AgentExtraSettingsPopup`、`AgentInfoPopup` | 4 | 待查 |
| [ClassicChatInputSection.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/classic/ClassicChatInputSection.kt) | 行内弹层 | 1 | 待查 |
| [ClassicChatSettingsBar.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/classic/ClassicChatSettingsBar.kt) | 行内弹层 | 2 | 待查 |
| [CharacterCardMemoryBindingSwitchConfirmDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/common/CharacterCardMemoryBindingSwitchConfirmDialog.kt) | `CharacterCardMemoryBindingSwitchConfirmDialog` | 1 | 待查 |
| [CharacterCardModelBindingSwitchConfirmDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/common/CharacterCardModelBindingSwitchConfirmDialog.kt) | `CharacterCardModelBindingSwitchConfirmDialog` | 1 | 待查 |
| [PendingMessageQueuePanel.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/common/PendingMessageQueuePanel.kt) | `PendingMessageQueuePanel` | 0 | 待查 |
| [ToolPromptManagerDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/common/ToolPromptManagerDialog.kt) | `ToolPromptManagerDialog` | 1 | 待查 |
| [WorkspaceChangeConfirmDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/WorkspaceChangeConfirmDialog.kt) | `WorkspaceChangeConfirmDialog` | 1 | 待查 |
| [ConversationDetailsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/details/ConversationDetailsScreen.kt) | `ConversationDetailsScreen`、`ExportConversationDialog` | 3 | 待查 |
| [AIChatScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/screens/AIChatScreen.kt) | `AIChatScreen` | 2 | 待查 |
| [ComputerScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/computer/ComputerScreen.kt) | `ComputerScreen` | 0 | 待查 |
| [CodeEditor.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/editor/CodeEditor.kt) | `CodeEditor` | 0 | 待查 |
| [CompletionPopup.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/editor/completion/CompletionPopup.kt) | `CompletionPopup` | 1 | 待查 |
| [FileManager.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/FileManager.kt) | 行内弹层 | 5 | 待查 |
| [WorkspaceManager.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceManager.kt) | `WorkspaceCommandExecutionDialog`、`ExpandableFabMenu` | 4 | 待查 |
| [WorkspaceReadOnlyDocumentPreview.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceReadOnlyDocumentPreview.kt) | `WorkspacePdfPage`、`renderPdfPage` | 0 | 待查 |
| [WorkspaceScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceScreen.kt) | `WorkspaceScreen` | 0 | 待查 |
| [WorkspaceSetup.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceSetup.kt) | 行内弹层 | 2 | 待查 |
| [WorkspaceFileSelector.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/WorkspaceFileSelector.kt) | `MentionSuggestionPanel` | 0 | 待查 |

## 悬浮小窗与全屏语音

| 文件 | 页面或容器声明 | 行内弹层调用数 | 状态 |
| --- | --- | --- | --- |
| [EditPanel.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/floating/ui/fullscreen/components/EditPanel.kt) | `EditPanel` | 0 | 待查 |
| [FloatingScreenOcrScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/floating/ui/screenocr/screen/FloatingScreenOcrScreen.kt) | `FloatingScreenOcrScreen` | 0 | 待查 |
| [AttachmentPanel.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/floating/ui/window/components/AttachmentPanel.kt) | `FloatingAttachmentPanel` | 0 | 待查 |
| [FloatingChatWindowScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/floating/ui/window/screen/FloatingChatWindowScreen.kt) | `FloatingChatWindowContent` | 0 | 待查 |
| [FloatingChatWindowViewModel.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/floating/ui/window/viewmodel/FloatingChatWindowViewModel.kt) | `toggleAttachmentPanel`、`showInputDialog`、`hideInputDialog` | 0 | 待查 |

## 扩展与市场

| 文件 | 页面或容器声明 | 行内弹层调用数 | 状态 |
| --- | --- | --- | --- |
| [MCPServerConfigContent.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/components/dialogs/content/MCPServerConfigContent.kt) | `MCPServerConfigContent` | 0 | 首轮已审，见P6c反审项 |
| [MCPServerDetailsContent.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/components/dialogs/content/MCPServerDetailsContent.kt) | `MCPServerDetailsContent` | 0 | 首轮已审，见P6c反审项 |
| [MCPServerDetailsDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/components/dialogs/MCPServerDetailsDialog.kt) | `MCPServerDetailsDialog` | 1 | 首轮已审，见P6c反审项 |
| [MarketManageComponents.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/components/MarketManageComponents.kt) | `MarketManageDeleteDialog` | 1 | 待查 |
| [MCPInstallProgressDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/components/MCPInstallProgressDialog.kt) | `MCPInstallProgressDialog` | 1 | 首轮已审，见P6c反审项 |
| [AutomationFunctionExecutionDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/dialogs/AutomationFunctionExecutionDialog.kt) | `AutomationFunctionExecutionDialog` | 1 | 已查：只有定义，无生产调用入口 |
| [AutomationPackageDetailsDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/dialogs/AutomationPackageDetailsDialog.kt) | `AutomationPackageDetailsDialog` | 1 | 已查：只有定义，无生产调用入口 |
| [CreateScriptDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/dialogs/CreateScriptDialog.kt) | `CreateScriptDialog` | 1 | 待查 |
| [MCPPackageDetailsDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/dialogs/MCPPackageDetailsDialog.kt) | `MCPPackageDetailsDialog`、`MCPToolExecutionDialog` | 2 | 首轮已审，见P6c反审项 |
| [PackageDetailsDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/dialogs/PackageDetailsDialog.kt) | `PackageDetailsDialog` | 3 | 待查 |
| [ScriptExecutionDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/dialogs/ScriptExecutionDialog.kt) | `ScriptExecutionDialog` | 1 | 待查 |
| [UnifiedMarketDetailScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/market/UnifiedMarketDetailScreen.kt) | `UnifiedMarketDetailScreen`、`UnifiedMarketDetailCommentDialog` | 2 | 待查 |
| [ArtifactPublishScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/ArtifactPublishScreen.kt) | `ArtifactPublishScreen` | 6 | 待查 |
| [MarketAgreementDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/MarketAgreementDialog.kt) | `MarketAgreementDialog` | 1 | 待查 |
| [MCPCommandsEditDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/mcp/components/MCPCommandsEditDialog.kt) | `MCPCommandsEditDialog` | 1 | 首轮已审，见P6c反审项 |
| [MCPDeployConfirmDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/mcp/components/MCPDeployConfirmDialog.kt) | `MCPDeployConfirmDialog` | 1 | 待查 |
| [MCPDeployProgressDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/mcp/components/MCPDeployProgressDialog.kt) | `MCPDeployProgressDialog` | 1 | 首轮已审，见P6c反审项 |
| [MCPEnvironmentVariablesDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/mcp/components/MCPEnvironmentVariablesDialog.kt) | `MCPEnvironmentVariablesDialog` | 1 | 首轮已审，见P6c反审项 |
| [MCPConfigScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/MCPConfigScreen.kt) | `MCPConfigScreen`、`refreshMcpScreen`、`RemoteServerEditDialog`、`RemoteHeadersEditor` | 3 | 首轮已审，见P6c反审项 |
| [PackageEnvironmentVariablesSheet.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/PackageEnvironmentVariablesSheet.kt) | `PackageEnvironmentVariablesSheet`、`PackageEnvironmentVariablesSheetContent`、`PackageEnvironmentVariableEditor` | 1 | 待查 |
| [PackageManagerDialogs.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/PackageManagerDialogs.kt) | `PackageLoadErrorsDialog` | 1 | 待查 |
| [PackageManagerHeader.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/PackageManagerHeader.kt) | 行内弹层 | 1 | 待查 |
| [PackageManagerScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/PackageManagerScreen.kt) | `PackageManagerScreen` | 0 | 待查 |
| [PackageTabContent.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/PackageTabContent.kt) | `PackageTabContent` | 0 | 待查 |
| [PluginTabContent.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/PluginTabContent.kt) | `PluginTabContent` | 0 | 待查 |
| [RepoMarketPublishScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/RepoMarketPublishScreen.kt) | `RepoMarketPublishScreen`、`RepoPublishConfirmDialog` | 3 | 待查 |
| [SkillConfigScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/SkillConfigScreen.kt) | `SkillConfigScreen`、`SkillDetailDialog`、`SkillLoadErrorsDialog` | 3 | 待查 |
| [UnifiedMarketAuthorScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/UnifiedMarketAuthorScreen.kt) | `UnifiedMarketAuthorScreen` | 0 | 待查 |
| [UnifiedMarketDetailEntryScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/UnifiedMarketDetailEntryScreen.kt) | `UnifiedMarketDetailEntryScreen`、`MarketVersionHistoryDialog` | 2 | 待查 |
| [UnifiedMarketManageScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/UnifiedMarketManageScreen.kt) | `UnifiedMarketManageScreen`、`MarketManageRequestLoadingDialog`、`MarketManageReviewDialog`、`MarketManageReviewDialogContent`、`ManagePublishChooserDialog` | 3 | 待查 |
| [UnifiedMarketScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/UnifiedMarketScreen.kt) | `UnifiedMarketScreen`、`UnifiedMarketNotificationsScreen`、`UnifiedMarketCategoryScreen`、`MarketActionChooserDialog` | 1 | 待查 |

## 工具箱

| 文件 | 页面或容器声明 | 行内弹层调用数 | 状态 |
| --- | --- | --- | --- |
| [AppPermissionsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/apppermissions/AppPermissionsScreen.kt) | `AppPermissionsScreen`、`extractSectionContent` | 1 | 待查 |
| [AutoGlmOneClickToolScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/autoglm/AutoGlmOneClickToolScreen.kt) | `AutoGlmOneClickToolScreen`、`AutoGlmOneClickScreen` | 0 | 待查 |
| [AutoGlmToolScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/autoglm/AutoGlmToolScreen.kt) | `AutoGlmToolScreen`、`AutoGlmToolContent` | 0 | 待查 |
| [DefaultAssistantGuideScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/defaultassistant/DefaultAssistantGuideScreen.kt) | `DefaultAssistantGuideScreen`、`DefaultAssistantGuideContent` | 0 | 待查 |
| [FFmpegToolboxScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/ffmpegtoolbox/FFmpegToolboxScreen.kt) | `FFmpegToolboxScreen` | 0 | 待查 |
| [FileContextMenu.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileContextMenu.kt) | `FileContextMenu` | 1 | 待查 |
| [FileManagerDualPane.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileManagerDualPane.kt) | `FileManagerDualPane`（原 `FileListContent` 已移除） | 0 | 待查 |
| [FileManagerChrome.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileManagerChrome.kt) | 行内弹层 | 1 | 待查 |
| [NewFolderDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/NewFolderDialog.kt) | `FileManagerNewEntryDialog` | 1 | 待查 |
| [SearchDialogs.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/SearchDialogs.kt) | `SearchDialog`、`SearchResultsDialog` | 2 | 待查 |
| [FileManagerScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/FileManagerScreen.kt) | `FileManagerScreen` | 2 | 待查 |
| [HtmlPackagerScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/htmlpackager/HtmlPackagerScreen.kt) | `HtmlPackagerScreen` | 0 | 待查 |
| [LogcatScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/logcat/LogcatScreen.kt) | `LogcatScreen` | 0 | 待查 |
| [ProcessLimitRemoverScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/processlimit/ProcessLimitRemoverScreen.kt) | `ProcessLimitRemoverScreen` | 2 | 待查 |
| [ProcessLimitRemoverToolScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/ProcessLimitRemoverToolScreen.kt) | `ProcessLimitRemoverToolScreen` | 0 | 待查 |
| [ShellExecutorScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/shellexecutor/ShellExecutorScreen.kt) | `ShellExecutorScreen` | 2 | 状态/历史/输出首轮本地通过；其余布局反审 |
| [SpeechToTextScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/speechtotext/SpeechToTextScreen.kt) | `SpeechToTextScreen` | 0 | 待查 |
| [SpeechToTextToolScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/speechtotext/SpeechToTextToolScreen.kt) | `SpeechToTextToolScreen` | 0 | 待查 |
| [SqlViewerScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/sqlviewer/SqlViewerScreen.kt) | `SqlViewerScreen` | 0 | 分页/状态首轮本地通过；表格无障碍等剩余反审 |
| [SqlViewerToolScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/sqlviewer/SqlViewerToolScreen.kt) | `SqlViewerToolScreen` | 0 | 待查 |
| [StreamMarkdownDemo.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/StreamMarkdownDemo.kt) | `StreamMarkdownDemoScreen`、`ControlPanel` | 0 | 待查 |
| [TextToSpeechScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/texttospeech/TextToSpeechScreen.kt) | `TextToSpeechScreen` | 1 | 待查 |
| [TextToSpeechToolScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/texttospeech/TextToSpeechToolScreen.kt) | `TextToSpeechToolScreen` | 0 | 待查 |
| [ToolboxScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/ToolboxScreen.kt) | `ToolboxScreen`、`FileManagerToolScreen`、`TerminalToolScreen`、`TerminalAutoConfigToolScreen`、`AppPermissionsToolScreen`、`UIDebuggerToolScreen`、`FFmpegToolboxToolScreen`、`ShellExecutorToolScreen`、`LogcatToolScreen`、`ToolTesterToolScreen`、`DefaultAssistantGuideToolScreen` | 0 | 入口卡片已审并构建；包装子页继续 |
| [ToolTesterScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/tooltester/ToolTesterScreen.kt) | `ToolTesterScreen`、`ToolDetailsSheet` | 0 | 待查 |
| [ActivityMonitorPanel.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/uidebugger/components/ActivityMonitorPanel.kt) | `ActivityMonitorPanel` | 0 | 待查 |
| [UIDebuggerComponents.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/uidebugger/UIDebuggerComponents.kt) | `ElementInfoPanel`、`CreatePackageDialog` | 1 | 待查 |
| [UIDebuggerScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/uidebugger/UIDebuggerScreen.kt) | `UIDebuggerScreen` | 0 | 待查 |

## 工作流

| 文件 | 页面或容器声明 | 行内弹层调用数 | 状态 |
| --- | --- | --- | --- |
| [ConnectionMenu.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/workflow/components/ConnectionMenu.kt) | `ConnectionMenuDialog`、`ConnectionConditionDialog` | 2 | 待查 |
| [NodeActionMenu.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/workflow/components/NodeActionMenu.kt) | `NodeActionMenuDialog` | 1 | 待查 |
| [ScheduleConfigDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/workflow/components/ScheduleConfigDialog.kt) | `ScheduleConfigDialog` | 1 | 待查 |
| [WorkflowDetailScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/workflow/screens/WorkflowDetailScreen.kt) | `WorkflowDetailScreen`、`WorkflowExecutionLogDialog`、`NodeDialog`、`EditWorkflowDialog` | 12 | 待查 |
| [WorkflowListScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/workflow/screens/WorkflowListScreen.kt) | `WorkflowListScreen`、`TemplateTypeDialog`、`CreateWorkflowDialog` | 3 | 待查 |

## 记忆库

| 文件 | 页面或容器声明 | 行内弹层调用数 | 状态 |
| --- | --- | --- | --- |
| [DocumentViewDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/DocumentViewDialog.kt) | `DocumentViewDialog` | 1 | 待查 |
| [EditMemoryDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/EditMemoryDialog.kt) | `EditMemoryDialog`、`TagsEditor` | 1 | 待查 |
| [MemoryDialogs.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/MemoryDialogs.kt) | `MemoryInfoDialog`、`EdgeInfoDialog`、`EditEdgeDialog`、`LinkMemoryDialog`、`BatchDeleteConfirmDialog` | 5 | 待查 |
| [MemorySearchSettingsDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/MemorySearchSettingsDialog.kt) | `MemorySearchSettingsDialog` | 1 | 待查 |
| [MemorySearchSimulationDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/MemorySearchSimulationDialog.kt) | `MemorySearchSimulationDialog` | 1 | 待查 |
| [ToolTestDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/ToolTestDialog.kt) | `ToolTestDialog` | 1 | 待查 |
| [FolderNavigator.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/FolderNavigator.kt) | `FolderContextMenu`、`FolderCreateDialog`、`FolderRenameDialog`、`FolderDeleteDialog` | 7 | 待查 |
| [MemoryAppBar.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/MemoryAppBar.kt) | 行内弹层 | 1 | 待查 |
| [MemoryScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/MemoryScreen.kt) | `MemoryScreen` | 0 | 待查 |

## 助手与设置

| 文件 | 页面或容器声明 | 行内弹层调用数 | 状态 |
| --- | --- | --- | --- |
| [AvatarConfigSection.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/assistant/components/AvatarConfigSection.kt) | `MoodTypeEditorDialog` | 4 | 待查 |
| [VoiceAutoAttachComponents.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/assistant/components/VoiceAutoAttachComponents.kt) | `VoiceAutoAttachItemDialog`、`VoiceAutoAttachCreateDialog` | 3 | 待查 |
| [BackupDialogs.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/BackupDialogs.kt) | `DeleteConfirmationDialog`、`MemoryImportStrategyDialog`、`ProfileSelectionDialog`、`ChatHistoryExportSelectionDialog`、`ExportFormatDialog`、`ImportFormatDialog`、`ModelConfigExportWarningDialog` | 7 | 待查 |
| [CharacterCardAssignDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/CharacterCardAssignDialog.kt) | `CharacterCardAssignDialog` | 1 | 待查 |
| [CharacterCardDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/CharacterCardDialog.kt) | `CharacterCardDialog`、`CharacterCardToolAccessDialog`、`CharacterCardFixedModelPickerDialog`、`CharacterCardFixedMemoryProfilePickerDialog`、`FullScreenEditDialog` | 5 | 待查 |
| [CharacterGroupAssignDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/CharacterGroupAssignDialog.kt) | `CharacterGroupAssignDialog` | 1 | 待查 |
| [ColorPickerDialog.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/ColorPickerDialog.kt) | `ColorPickerDialog` | 1 | 待查 |
| [ModelNameTagEditor.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/ModelNameTagEditor.kt) | `ModelNameTagEditor`、`ModelManualAddDialog`、`ModelSortSheet` | 2 | 待查 |
| [UpstreamModelPickerSheet.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/UpstreamModelPickerSheet.kt) | `UpstreamModelPickerSheet` | 0 | 待查 |
| [ChatBackupSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ChatBackupSettingsScreen.kt) | `ChatBackupSettingsScreen` | 4 | 待查 |
| [ChatHistorySettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ChatHistorySettingsScreen.kt) | `ChatHistorySettingsScreen` | 6 | 待查 |
| [ContextSummarySettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ContextSummarySettingsScreen.kt) | `ContextSummarySettingsScreen` | 1 | 待查 |
| [CustomEmojiManagementScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/CustomEmojiManagementScreen.kt) | `CustomEmojiManagementScreen`、`CreateCategoryDialog` | 5 | 待查 |
| [ExternalHttpChatSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ExternalHttpChatSettingsScreen.kt) | `ExternalHttpChatSettingsScreen` | 1 | 待查 |
| [FunctionalConfigScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/FunctionalConfigScreen.kt) | `FunctionalConfigScreen` | 1 | 待查 |
| [GitHubAccountScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/GitHubAccountScreen.kt) | `GitHubAccountScreen` | 0 | 待查 |
| [GlobalDisplaySettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/GlobalDisplaySettingsScreen.kt) | `GlobalDisplaySettingsScreen` | 0 | 待查 |
| [LanguageSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/LanguageSettingsScreen.kt) | `LanguageSettingsScreen` | 0 | 待查 |
| [LayoutAdjustmentSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/LayoutAdjustmentSettingsScreen.kt) | `LayoutAdjustmentSettingsScreen` | 0 | 待查 |
| [MnnModelDownloadScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/MnnModelDownloadScreen.kt) | `MnnModelDownloadScreen` | 1 | 待查 |
| [ModelConfigScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ModelConfigScreen.kt) | `ModelConfigScreen` | 5 | 待查 |
| [ModelPromptsSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ModelPromptsSettingsScreen.kt) | `ModelPromptsSettingsScreen`、`GroupCardDialog`、`TagDialog`、`importPromptTagsFromJsonContent` | 16 | 待查 |
| [PersonaCardGenerationScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/PersonaCardGenerationScreen.kt) | `PersonaCardGenerationScreen` | 5 | 待查 |
| [SpeechServicesSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/SpeechServicesSettingsScreen.kt) | `SpeechServicesSettingsScreen` | 9 | 待查 |
| [TagMarketScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/TagMarketScreen.kt) | `TagMarketScreen` | 1 | 待查 |
| [ThemeSettingsContentEditor.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/theme/ThemeSettingsContentEditor.kt) | `ThemeSettingsContent`、`ThemeSettingsContentEditor` | 0 | 待查 |
| [ThemeSettingsTabs.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/theme/ThemeSettingsTabs.kt) | `ThemeSettingsTabbedContent` | 0 | 待查 |
| [ThemeSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ThemeSettingsScreen.kt) | `ThemeSettingsScreen` | 0 | 待查 |
| [TokenUsageStatisticsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/TokenUsageStatisticsScreen.kt) | `TokenUsageStatisticsScreen` | 3 | 待查 |
| [ToolPermissionSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ToolPermissionSettingsScreen.kt) | `ToolPermissionSettingsScreen`、`ToolSelectorDialog` | 1 | 待查 |
| [UserPreferencesSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/UserPreferencesSettingsScreen.kt) | `UserPreferencesSettingsScreen` | 3 | 待查 |
| [WaifuModeSettingsScreen.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/WaifuModeSettingsScreen.kt) | `WaifuModeSettingsScreen` | 0 | 待查 |
| [AdvancedSettingsSection.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/AdvancedSettingsSection.kt) | `ApiKeyEditDialog` | 2 | 待查 |
| [ModelApiSettingsSection.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/ModelApiSettingsSection.kt) | `ApiProtocolSelectionSheet`、`ApiProviderSelectionSheet` | 5 | 待查 |
| [ModelParametersSection.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/ModelParametersSection.kt) | `AddCustomParameterDialog` | 2 | 待查 |
| [ThemeSettingsCoreSections.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/ThemeSettingsCoreSections.kt) | `BubbleFontStyleEditor`、`BubbleImageStyleEditor` | 0 | 待查 |

## 终端子模块

| 文件 | 页面或容器声明 | 行内弹层调用数 | 状态 |
| --- | --- | --- | --- |
| [TerminalScreen.kt](../../../terminal/src/main/java/com/ai/assistance/operit/terminal/main/TerminalScreen.kt) | `TerminalScreen` | 0 | 待查 |
| [SettingsScreen.kt](../../../terminal/src/main/java/com/ai/assistance/operit/terminal/ui/SettingsScreen.kt) | `SettingsScreen`、`SourceSelectionDialog`、`AddCustomSourceDialog` | 9 | 待查 |
| [SetupScreen.kt](../../../terminal/src/main/java/com/ai/assistance/operit/terminal/ui/SetupScreen.kt) | `SetupScreen` | 1 | 待查 |
| [SSHConfigScreen.kt](../../../terminal/src/main/java/com/ai/assistance/operit/terminal/ui/SSHConfigScreen.kt) | `SSHConfigScreen`、`SSHConfigEditDialog` | 2 | 待查 |
| [TerminalHome.kt](../../../terminal/src/main/java/com/ai/assistance/operit/terminal/ui/TerminalHome.kt) | 行内弹层 | 1 | 待查 |
| [VirtualKeyboardCustomizationDialog.kt](../../../terminal/src/main/java/com/ai/assistance/operit/terminal/ui/VirtualKeyboardCustomizationDialog.kt) | `VirtualKeyboardCustomizationDialog`、`VirtualKeyboardKeyEditor` | 1 | 待查 |
| [CanvasTerminalCompose.kt](../../../terminal/src/main/java/com/ai/assistance/operit/terminal/view/canvas/CanvasTerminalCompose.kt) | `CanvasTerminalScreen` | 0 | 待查 |

## 继续覆盖的控制面

- `ScreenRouteRegistry` 的原生路由和 `AppRouteCatalog` 的 ToolPkg 动态入口；对照当前可达性，区分工具箱注册项与未注册示例。
- 预置 ToolPkg 的 Compose DSL / Web 页面、脚本详情与配置入口。第三方任意安装内容的源码不在仓库内，宿主加载、错误、关闭与生命周期属于本轮。
- 顶栏终端与工作区共享 `ComputerScreen`、Workspace 相关宿主；终端模块的 UI 路径按实际引用进一步展开。
- 按钮内部触发的系统文件选择、权限与分享窗口检查调用参数、取消/错误回调和资源清理；真实系统窗口效果保持待设备验收。

## P6a 已展开叶子与当前证据

本表补充文件级定位表，未列为已审的功能继续执行本专项。所有视觉、触控、实际 provider I/O 均为 `verification_pending`。

| 叶子功能 | 当前源码结论与处理 | 本地证据/剩余工作 |
| --- | --- | --- |
| 文件标签打开/切换/关闭 | 以路径标识，关闭非活动标签保持当前文件 | WorkspaceEditorState 测试通过 |
| 保存按钮 | 等待写入 success，保存期间新编辑继续为未保存 | 保存失败/竞态测试通过 |
| 未保存关闭弹窗 | 确认固定路径，保存中禁用重复动作，成功且正文一致才关闭 | 保存增量 APK 通过 |
| 编辑、格式化、撤销、重做 | 回调绑定文件路径，编辑器引用核对活动文件 | 源码修复，设备键盘操作待验收 |
| 外部更新 | 单文件快照发布，不覆盖 dirty/保存中/已关闭文件 | 状态测试通过 |
| 工作区切换与页面退出 | 既有 ChatViewModel 持有同一编辑状态；页面实例按 chat/环境隔离 | 7m45s 构建通过；进程重建不持久化大文件正文 |
| 文件面板与返回 | 面板优先消费 Back，目录列表保持模态触控 | 源码修复；触控/Back 待设备 |
| 目录刷新/跳转/空态 | 成功后发布路径/环境/列表，失败显示错误，空目录明确提示 | 文件浏览器增量 APK 通过；空态在最新批次 |
| 新建文件/文件夹弹窗 | 单文件名校验、已有目标检查、成功后关闭并刷新 | 文件名测试通过；provider 原子创建能力仍待查 |
| 删除文件/文件夹弹窗 | 固定路径二次确认，实际失败保留弹窗和错误 | 文件浏览器增量 APK 通过 |
| 目录书签命名/移除 | 等持久化结果；移除前检查工作区引用；不撤销其他功能可能共享的系统授权 | 7m45s 构建通过，实际 SAF 待设备 |
| 内置模板选择/导入 | IO 中创建 staging，成功才发布，失败不覆盖既有目录 | 3 项创建测试、APK 通过 |
| ToolPkg 模板选择/导入 | 复用相同 staging 发布，沿原 PackageManager 导入与配置校验 | 创建测试通过；实际包导入待验证 |
| 已有目录重新绑定 | 明确入口复用已有工作目录，不复制新模板覆盖原文件 | 创建增量 APK 通过 |
| 解除绑定与重命名 | 未保存文件先处理；重命名原有异步弹窗仍需最终复查 | 部分已审 |
| 回滚/编辑重发确认 | 持久 SQL 前驱，预览加载/失败禁用确认，执行先停止对话 | 5 个 SQLite 场景、回滚增量 APK 通过 |
| 备份文件恢复 | 清单/对象失败可见、相对路径与 SHA-256 校验、先写后删 | 4 项恢复顺序测试通过；跨 SQL/文件系统无共同事务 |
| 浏览器预览/命令预览 | 已定位唯一 owner 与释放链，仍需逐按钮复查 | 待完成 |
| 工作区导出与命令执行弹窗 | 已定位入口，保留既有任务 owner | 待完成 |
| 终端会话/设置/SSH/键盘/环境 | 设置/SSH/键盘/环境主题、SSH 参数持久化、软件源及草稿保持已实现 | 软件源批次父 APK 通过；维护与剩余叶子继续验证 |

## P6a 终端叶子审查队列

以下按叶子跟踪具体实现与剩余边界；局部源码修复不替代整套终端功能与设备验收。

| 叶子功能 | 当前证据与下一步 |
| --- | --- |
| setup/home/settings 切换与系统返回 | TerminalScreen 已有唯一即时路由与 systemBackEnabled，保留原生画布生命周期 |
| 终端设置/SSH/键盘编辑主题 | SettingsTheme 与 SetupScreen 已消费宿主语义色；终端画布独立保留，软件源批次父 APK 通过 |
| SSH 配置添加/编辑/删除、启停 | 等待持久化、错误保留；端口和心跳校验测试通过。连接应用确认、实际环境投影与配置损坏恢复入口已通过后续父 APK |
| SSH 密码/私钥/密钥密码、心跳、反向隧道 | 可见字段校验，未暴露字段与禁用分组原值保留；心跳毫秒溢出和凭据空格测试通过 |
| SSH 工具缺失 / OpenSSH 缺失弹窗 | 已接现有 setup 路由并通过父 APK；长正文滚动与选择已实现，本批验证中 |
| 字号 / 渲染 FPS 弹窗 | 纠正字号单位为 px，保留小数；提交值冻结、等待保存、失败保留，字体批次父 APK 通过 |
| 自定义字体路径 / 系统字体名称 | 自定义文件校验可读/有效字体，加载失败显示设置入口；保持原配置 owner 与既有系统字体解析行为，父 APK 通过 |
| 重置环境 / 清缓存确认 | 防重复与冲突、维护取消和有界进程等待、FTP互斥、文件错误传播已通过 86 项中84执行测试与父 APK；真实挂载/FTP待设备 |
| 软件源选择 / 删除 / 添加 | 等待保存/删除、固定目标确认、选中项纠正、URL 校验与列表脱敏均通过父 APK；生效由原启动/安装 owner 控制 |
| 虚拟键盘布局 / 单键编辑 / 重置 | 标题自适应，切换动作留正文；提交冻结、防重复与原布局冲突检测通过父 APK；Activity 已接管 orientation/screenSize 等 configChanges，不能仅凭 remember 判定旋转丢失；实际设备仍待验证 |
| 会话新建 / 选择 / 删除 | 创建防重复和进度、异步取消归属与移除终止等待模块通过；删除确认与选择剩余叶子继续审查 |
| 原始键盘 / 命令输入 / 粘贴 / 选择 | 人工输入、中断固定触发时会话；失败可见、正文成功后清理模块通过；剪贴板/文本选择与原始按键顺序继续审查 |

## P6b 对话详情叶子覆盖

以下实现已通过 19 项相关测试与 P6b 父 APK。IME、屏幕阅读器、窄屏/大字体、真实存储错误与进程中断均保留 `verification_pending`。

| 叶子功能 | 实际审阅与处理 |
| --- | --- |
| 顶栏入口 / 再次点击 / 关闭 | 打开任务防重，异步发布检查聊天和面板代次；页面按聊天隔离 |
| 完整性 / 事件数 / 消息数 / 存储 / Provider / 链头 | 总事件数使用审计元数据；成功/警告色使用浅深语义色；长文字省略 |
| 时间线 / 对话 / Raw 标签 | 48dp 点击区域与 Tab 选中语义；搜索和列表位置分别保存 |
| 搜索 / 清空 / 无匹配 / 更早事件 | 明确已加载事件范围；空匹配保留分页入口和说明，清空按钮 48dp |
| 初次加载 / 实时事件 / 分页失败 | 现有分页 owner 保持，增加身份与代次校验、加载反馈和显式重载 |
| 全部展开 / 收起 / 新事件提示 | 稳定 eventId；计算原列表底部时扣除新增匹配项；不把被筛掉的事件加入提示计数 |
| 事件 payload / 元信息 / 二进制 / 复制 | 保持原解密与选择链；离屏/折叠释放，取消继续传播，错误不显示堆栈并可重载 |
| 当前消息展开 / 修订弹窗 | 冻结原消息，保存期间禁用，失败保留正文；仓储锁内检查原文未变化，弹窗正文可滚动 |
| 批注弹窗 | trim 校验，等待实际写入，失败留草稿与局部错误，重复动作禁用 |
| 导出格式弹窗 | 两种现有格式保持；进行中反馈与防重，失败留弹窗，长说明可滚动 |
| 导出文件发布 / 失败 / 已生成但记录失败 | 同目录临时文件完整写入后发布，不覆盖旧文件；清理失败产物；部分完成错误提供已生成文件位置 |
| 后台页面 / 模态返回 | 子视图 Back 服从宿主可见性与上层弹窗；后台隐藏遗留弹窗 |

## P6a 路径、配置与预览补充覆盖

2026-09-09 父 APK `89169589` 对应以下已完成本地验证；配置身份校验是派发前检查，不具有跨文件系统/终端的共同原子事务。

| 叶子功能 | 当前实现与证据 |
| --- | --- |
| 配置读取/重载/错误修复入口 | Android/Linux 读取失败保持错误；仅确认缺失才采用原默认值，配置 4 项测试与 APK 通过 |
| 命令按钮/结果/工作目录 | 固定配置来源与环境，核对实际 provider；路径 Shell 引用、命令状态测试与 APK 通过 |
| 工作区拖动动作菜单/文件标签 | 标准滚动菜单、拖动结束保存位置、实际编辑器撤销/重做与 Tab 选中语义；APK 通过 |
| HTML/PDF/文档/图片预览 | 非本地独立临时文件释放，PDF 失败终态、像素上限，编辑器主题与补全定位；APK 通过 |
| 导出入口/文件树/结果弹窗 | 非本地隐藏不适用的导出入口；显式栈、祖先循环/越界检查、空目录与取消；3 项导出树测试与 APK 通过 |
| 本地/虚拟预览路径 | canonical 根边界与虚拟 dot segment 规范化；2 项路径测试与 APK 通过 |
| 后台工作区/模板/文件面板弹窗 | 宿主可见性约束弹层与 Back，APK 通过；触控和 WebView 生命周期待设备 |
| 人工输入顺序/草稿修订 | OrderedTerminalInput 2 项测试；终端共 88 项中 86 执行通过、2 WSL 跳过；APK 通过 |

## P6c 环境配置首批覆盖

| 叶子功能 | 当前处理与验证状态 |
| --- | --- |
| 环境变量全部/指定容器入口 | 固定打开时字段与原值，不随外部目录刷新覆盖草稿；本批测试中 |
| 分类/搜索/分组展开 | 保留既有排序与过滤策略，展开状态不随配置刷新重置 |
| 全局与容器字段保存 | 只提交变更键，仓储各自锁内原值校验与批次写盘；冲突/失败保留草稿，两个存储没有共同事务 |
| 保存/取消/关闭/拖动/代理跳转 | 保存等待、防重；未保存退出确认；拒绝拖动关闭返回部分展开；窄屏底部按钮可换行 |
| 敏感输入/数字/枚举/布尔值 | 敏感输入和默认值隐藏，选中语义与清除显式选择；主要输入与选项点击区提升 |
| 扩展其余详情/执行/删除/导入/市场 | 尚未闭合；详情删除吞 false、子包切换竞态与执行草稿快照已列入下一增量 |

P6c 环境配置首批15项测试与父 APK 已通过；详情/脚本首批编译与父 APK 已通过，随后修正默认工具基底、流式结果回传重置和源文件删除确认，最新 APK 构建中。PackageManager 所有者继续调查发现：启用名单与子包状态分开 apply，读改写不全在现有 initLock 内；不能直接把整个 disable/delete 包进 initLock，因为引擎销毁会同步等待 QuickJS 线程，必须先设计发布与资源释放的安全顺序。

## P6c Skill 详情增量

| 叶子功能 | 当前实现与证据 |
| --- | --- |
| 列表刷新/失败 | 单个 UI 刷新锁，失败保留原列表并提供重载；本批验证中 |
| 详情打开/关闭/重载 | LaunchedEffect 绑定选中对象，取消旧读取；读取失败不当作空 Markdown 或永久进度 |
| 目录预览/数量/隐藏条目 | 显式栈排序遍历，链接只展示，不枚举目标；缺失目录失败，迭代可取消；4 项测试中 |
| SKILL.md 展开/收起 | 保留既有 Markdown 渲染，读取有明确结果状态 |
| 删除/取消/错误 | 固定技能目录确认，IO 执行等待，失败留确认，成功后刷新 |
| GitHub/ZIP/手动导入及附件 | 仍需修复源文件临时路径、最终目录边界、staging 发布和错误结果不关闭；详见专项 index |

## P6c MCP 覆盖与剩余反审

| 叶子功能 | 当前事实与下一步 |
| --- | --- |
| 原始 JSON 配置编辑 | 固定插件与原值、同步正文提交、等待/冲突/失败保留、未保存关闭确认已实现；整页滚动补充正在构建 |
| 远程新增/编辑、端点/令牌/Headers | 保存等待、原值字段合并、HTTP(S)/Header校验、防重、认证隐藏和未保存关闭确认已通过本地验证 |
| 本地详情/卸载 | 操作固定身份、卸载确认、异常结果/明确重试、活动操作防重、配置与底栏实际测量已验证；图标/长说明与工具读取重载补充正在构建 |
| 部署确认/命令/环境变量 | 目标/请求代次、失败清旧结果、草稿首次初始化、防重复部署、输出上限/滚动、变量隔离与取消清理已验证；确认弹窗和后台可见性继续反审 |
| 配置持久化与重载 | 同锁读改写、完整文件发布后更新内存、读取失败阻止覆盖和重载入口已验证；JSON合并完整类型校验与脱敏错误补充测试通过，正在构建 |
| ZIP与仓库导入/文件发布 | 目录表预检、路径边界、独立staging、旧目录延迟替换、no-follow删除、保留配置文件名、网络清理与操作重试已验证；ZIP选择器统一Activity Result和唯一项目定位补充正在构建 |
| 工具详情与执行弹窗 | 读取失败可重载、固定参数快照、Main发布、取消传播、运行中关闭确认已实现并编译，当前等待父APK |
| 剩余反审 | 页面后台弹窗可见性、部署确认布局、刷新/启停失败反馈已包含22146 APK；MCP运行时与SSH文件环境匹配、配置状态文件及后台草稿连续性继续反审；市场叶子仍未全面审阅 |

## P6c 市场安装与评论增量

| 场景 | 当前事实与处理 |
| --- | --- |
| Skill更新 | 原入口在下载前递归删除旧根；已移除，改为SkillManager锁内验证原目录及市场来源，完整新目录连同标记发布；准备失败保留旧安装，改名/多个旧目标明确拒绝。文件发布及元数据测试53864进行中 |
| MCP市场资料 | 去除导入前删除；市场元数据锁内批次合并，保留最新连接、启停和安装路径；配置与市场标记仍不是共同事务 |
| 安装按钮 | Browse/Detail ViewModel的Job完成回调释放原MarketInstallStateStore占位，刷新异常与取消不会永久保留安装中；刷新错误单独说明 |
| 评论新增/编辑 | 新增/编辑等待明确成功，失败保留正文、内联错误与手动重试；退出未保存确认，删除固定目标确认；同步防重与取消释放；旧读取不会覆盖已成功编辑/删除的缓存。4项行为测试通过，旋转、后台恢复与真实服务仍待验证 |
| 下载文件 | 外部assetName只提取受支持格式，独立随机临时文件，失败/取消/空文件/校验失败清理；下载和hash循环检查取消，3项文件行为测试通过 |
| 列表/分类/搜索 | 请求代次、同步分页占位、本地安装投影身份，失败保留重试，分类空/错误终态；筛选已加载条目的说明与继续加载入口；48dp控件、分组实际布局索引、排序回顶部与搜索Back。3项测试和40812 APK已通过 |
| 作者/通知 | 作者两种读取分离、失败可重载、防重与离页取消；通知全文与打开条目、刷新错误、本地化、后台弹层约束；7993的6项与2665的8项测试通过，27971 APK通过 |
| 管理/撤回/详情读取 | 账号绑定、隐藏分类不自动请求、同步防重、失败留确认与重试、离页取消迟到跳转；98391的2项测试和58969 APK通过；真实服务待验证 |
| Repo发布/元数据/新版本/分类/确认 | 原ViewModel提交状态、内容快照、防重、草稿恢复与未保存返回、贡献者描述编辑、确认滚动与分类重试；12914的2项测试及67156 APK通过 |
| 制品发布/来源/Release/资产/创建仓库确认 | 表单等待禁用、目录目标失效、同步提交防重、本地读取重载、后台弹层约束与未保存返回；74238目录与竞态3项通过，41429 APK通过 |
| 制品上传后登记失败 | 资产不删除，原内容与账号的单独登记恢复；同版本不同资产拒绝替换。39942的恢复3项和HTTP分类2项通过，41429 APK通过；未知提交保持当前页占位，跨进程事实仍需后续处理，不能宣称远端验收通过 |
| 市场其余反审 | 脚本更新仍先deletePackage后导入，须在原PackageManager闭合发布与引擎释放；发布/管理与账号切换下缓存/请求隔离尚待实现与验证；市场标记独立文件持久化与旧目录扫描一致性仍需反审；协议/登录/选择器完整叶子未关闭 |

此表是增量记录，不代表市场或P6c整体已完成。没有执行真实市场安装、评论、发布或网络请求。

2026-09-09：安装增量APK为`0A7FB05F`；目录定位3项测试、扩展后的配置解析6项测试通过，当前38712父APK构建中。完整MCP域尚未标记完成。

## P6d 工具箱首批

| 叶子 | 当前覆盖与边界 |
| --- | --- |
| 入口/卡片/导航 | 延迟导航移除、动态回调、随字体增高；63095 APK通过 |
| Shell输入/发送/历史/清除/输出 | 页面ViewModel同步占位与草稿保护，会话内最近100条历史，确认与完整选择输出；3项及32418 APK通过 |
| SQL参数/查询/分页/错误 | 结果绑定查询和页大小，明确页大小校验；4项及20228 APK通过；Canvas表格无障碍、单元格查看与复杂SQL回查待继续 |
| 日志导出/快照/清除/结果 | 同原写入队列、实际清除反馈、稳定快照与取消清理；5项及48786 APK通过；现场SAF与设备待验证 |
| 工具测试网格/详情/参数/结果/批量/停止 | 用例身份、互斥、真实范围确认、串行调度、独立目录与完整正文已实施；21586验证中 |
| TTS/STT/FFmpeg/HTML打包/权限/进程/默认助手/UI调试/自动化 | 继续逐页反审，不能将入口清单当作完成 |

## P6e–P6g 反审顺序

工作流先检查列表刷新代次、编辑器草稿与节点连接唯一身份，再检查触发器/变量/运行停止/日志及导入导出确认。记忆库按文件夹与文档读取、搜索设置/模拟/工具测试、编辑/链接/图谱、导入删除逐层检查。助手与 AI 设置按模型协议与参数、提示词、角色群组、上下文、工具权限、语音、历史、备份及每个选择弹层检查。每项都需要错误/加载/空态/离页取消和长文本证据；外部模型、语音、设备与文件 provider 仍单独标记 `verification_pending`。
