---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
baseline: c036a03e
---

# 设置页信息架构与统一视觉

## 2026-08-20 AI助手设置全面重构

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE VERIFICATION PENDING`。
本增量只重构“设置首页 → AI助手”及其所有可达子页、弹层、字段和操作，不修改设置首页另外十一项
的入口矩阵。Kiyori 尚未公开发行，旧 Operit 设置 UI 可以彻底清理；兼容标识、持久化格式、
业务 repository/preferences/service 和现有 `Screen` route 必须保留唯一。

### 当前页面矩阵

| 新根分组 | 根入口 | 当前页面/子页 | 唯一 owner | 本轮 UI 与逻辑重点 |
| --- | --- | --- | --- | --- |
| 模型与生成 | 模型与 API | `ModelConfigScreen`、`MnnModelDownloadScreen`、Provider/模型/请求头/参数/上下文面板 | `ModelConfigManager`、`ModelConfigSaveCoordinator` | 配置选择、创建/重命名/删除、敏感字段、连接测试、折叠参数与错误反馈 |
| 模型与生成 | 功能模型分配 | `FunctionalConfigScreen` | `FunctionalConfigManager` | 按功能分配、模型能力提示、连接测试、批量重置确认 |
| 模型与生成 | 提示词与角色 | `ModelPromptsSettingsScreen`、`TagMarketScreen`、角色/群组/标签编辑弹窗 | `CharacterCardManager`、`CharacterGroupCardManager`、`PromptTagManager`、`ActivePromptManager` | 三标签工作台、排序、导入导出、市场、角色生成入口、确认与反馈 |
| 个性化与交互 | 用户资料 | `UserPreferencesSettingsScreen`、旧档案底部面板 | `UserProfileDocumentRepository` | 编辑/预览、字符限制、保存、未保存 Back、重置与旧档案 |
| 个性化与交互 | 虚拟形象 | `KiyoriAvatarSettingsPage`、Avatar 导入、动作/情绪映射弹窗 | `AssistantConfigViewModel`、`AvatarRepository` | 预览、导入、模型类型、动作映射、字段和弹窗一致性 |
| 个性化与交互 | 回复与表情 | `WaifuModeSettingsScreen`、`CustomEmojiManagementScreen` | `WaifuPreferences`、自定义表情 repository | 去除用户可见旧 Waifu 标题，按回复节奏/文本/表情/自拍分类，统一自动保存反馈 |
| 语音 | 文本转语音 | `SpeechServicesSettingsScreen(TEXT_TO_SPEECH)` | `SpeechServicesPreferences` 与 TTS runtime | 引擎、音色、速率、清洗、HTTP/WebSocket 配置、测试与选择面板 |
| 语音 | 语音转文本 | `SpeechServicesSettingsScreen(SPEECH_TO_TEXT)` | `SpeechServicesPreferences` 与 STT runtime | 本地/远程引擎、端点、鉴权、模型、测试和依赖禁用态 |
| 语音 | 语音唤醒 | `KiyoriVoiceWakeupSettingsPage`、个人模板、自动附加弹窗 | `WakeWordPreferences`、`PersonalWakeEnrollment` | 权限、监听、模式、唤醒词、模板录入、问候、新对话和附件规则 |
| 上下文与工具 | 上下文与总结 | `ContextSummarySettingsScreen` | 当前聊天绑定的 `ModelConfigManager` 配置与历史保留 preferences | 绑定配置说明、自动保存、数值校验、自定义规则和历史媒体保留 |
| 上下文与工具 | AI 工具授权 | `ToolPermissionSettingsScreen`、工具选择弹窗 | `ToolPermissionSystem`、`AIToolHandler` | 全局默认、允许/禁止例外、搜索、空状态、工具说明和移除语义 |
| 服务与用量 | AI 用量与费用 | `TokenUsageStatisticsScreen`、定价/重置弹窗 | `ApiPreferences`、`ChatHistoryManager` | 总览优先、模型明细、计费模式、汇率、定价、单项/全部重置 |
| 服务与用量 | 局域网与自动化 | `ExternalHttpChatSettingsScreen` | `ExternalHttpApiPreferences`、`AIForegroundService` | 服务状态、端口、令牌遮蔽/复制/重置、Web/API 地址、示例和风险提示 |

“人设卡生成”作为 `PersonaCardGenerationScreen` 继续存在，但不再是根页重复入口；它由
“提示词与角色”的主操作进入，并继续使用 `PersonaCardChatHistoryManager` 和现有模型调用链。

### 新根页排序与文案

从上到下固定为：

1. `模型与生成`：`模型与 API / 功能模型分配 / 提示词与角色`
2. `个性化与交互`：`用户资料 / 虚拟形象 / 回复与表情`
3. `语音`：`文本转语音 / 语音转文本 / 语音唤醒`
4. `上下文与工具`：`上下文与总结 / AI 工具授权`
5. `服务与用量`：`AI 用量与费用 / 局域网与自动化`

排序原则是首次可用性和决策依赖优先：先配置可用模型，再分配功能模型与角色提示；随后处理个人
表达和语音；最后放置高级上下文、工具安全、统计和外部集成。根页不再使用硬编码中文，七份当前
语言资源使用同一 string key 集合。

### 双层 Settings Surface

普通列表型设置继续使用 `KiyoriCollapsingSettingsPage`。长表单、编辑器、统计和多标签页面使用
新的 Kiyori 紧凑设置工作台：

- 顶栏自身消费状态栏安全区，背景不透明，Back 使用
  `LocalKiyoriEmbeddedSettingsNavigation` / 页面真实未保存状态；
- 页面背景、卡片、字段、分隔线、开关、按钮、Snackbar、弹窗和底部面板全部位于
  `KiyoriSettingsTheme`；
- 顶栏高度、水平边距、`16dp` 卡片圆角、无阴影层级、`14dp` 字段圆角和底部安全区统一；
- 编辑器、对话、图表和多标签工作台可以保留自己的内部滚动与状态，不强行嵌套到第二个
  `LazyColumn`；
- `OperitScreens` 对这些页面设置 `usesEmbeddedSettingsTopBar = true`，删除外层 Operit
  设置 TopAppBar 的视觉所有权，但不改变 route ID、route stack 或保活语义。

### 控件、弹层与反馈

- 导航行、开关行和单选项分别使用共享设置行、Material Switch 与统一底部选择面板。
- 表单字段必须显示真实单位、范围、错误和生效时机；自动保存项不再同时显示“保存”按钮。
- 默认动作使用 Filled/Tonal，次操作使用 Outlined/Text；破坏性动作只使用错误色并经过确认。
- API Key 与 Bearer Token 默认遮蔽；“显示”“复制”“保存”“连接测试”“重启服务”是独立动作。
- 成功、失败和异步状态统一进入 Snackbar 或页面内状态块；不再新增 Toast/临时底部卡片混用。
- 对话框标题说明当前对象，确认按钮使用明确动词；删除/重置后关闭弹层并更新唯一 owner 的投影。
- 工具搜索、无 Token 记录、无角色、无标签、无网络地址等情况必须有明确空状态与下一动作。

### 已确认的功能修复

1. 功能模型“重置全部”增加确认，避免一次误触改写所有功能映射。
2. Token 统计读取聊天数量失败时记录日志并显示非阻塞错误，不再静默吞掉异常。
3. 外部接口令牌默认遮蔽；重置令牌前确认会使既有客户端失效。
4. 工具选择搜索无结果时显示明确空状态；工具列表使用稳定排序。
5. 用户资料、角色编辑器和其他有草稿的页面只在真实未保存时拦截 Back；退出动画中的旧页面不能
   抢占下一次 Back。
6. 角色生成的引导文案迁入资源，不再按进程 Locale 手写中英分支。
7. 上下文页“重置所有设置”改为与真实操作一致的“恢复历史媒体默认值”，不会改写上下文窗口、
   总结开关或自定义规则。
8. 本地模型列表的初始加载、刷新与重试共用一个刷新入口；删除结果进入可见反馈。
9. 自定义表情内置分类不可删除；分类、表情和默认集合的破坏性动作继续使用确认对话框。
10. 语音 API Key 默认遮蔽；个人唤醒录音异常会记录日志、显示错误并释放录音状态；清除模板和
    删除自动附件必须确认，自动新对话分组在对应开关关闭时不可编辑。
11. Avatar 预览改用零阴影 Settings 卡片；预览加载失败同时写日志和页面反馈；删除模型和自定义
    情绪均在确认后执行。
12. 语音自动附件按类型稳定排序，网格高度覆盖窄屏多行内容；系统 TTS 语言筛选不再混入其他语言
    音色，音色读取失败显示稳定错误文案。
13. 模型自定义请求头的持久化 JSON 损坏时保留原始配置，进入显式不可编辑状态并停止注册保存与
    自动保存；页面显示稳定错误说明，不再把解析失败伪装为空请求头。

### 里程碑与验证

1. [DONE] 根页本地化、五组十三项、共享紧凑工作台、路由标题与静态测试。
2. [DONE] 用户资料、工具授权、用量统计、局域网与自动化。
3. [DONE] 模型/API、功能模型、上下文、MNN。
4. [DONE] 提示词与角色、角色生成、回复与表情、自定义表情、标签模板。
5. [DONE] 虚拟形象、语音唤醒、TTS、STT 的最小弹层复核。
6. [DONE] `CONTEXT.md`、资源、测试和反向零引用检查。
7. [DONE] 定向 JVM/Kotlin、architecture、formal readiness、Markdown/XML/localization、
   `git diff --check` 与串行 Debug APK。
8. [IN PROGRESS] 精确审计、提交、推送和 local/tracking/remote `0/0` 对账。
9. [PENDING DEVICE] 目标设备逐页验证；本任务不安装或操作设备。

第一实现回滚点为本任务基线 `main@5a63f2b59074dbc711c715e3f7a294827c035bb9`。后续每个里程碑
以可编译、可测试的当前树作为新回滚点，不保留隐藏旧页面、并行状态或运行时回退逻辑。

### 当前自动化验证证据

- architecture boundary `PASS (phase=m03)`；ARCH040 M-05A1 的 Settings theme consumer snapshot
  已同步本轮实际新增消费者，未扩大设计 owner 或 import root。
- CI Python `220/220`、formal readiness `PASS`、ToolPkg sync `7/7`；七份语言资源 XML 可解析，
  本轮新增 83 个资源键均在七种语言中各出现一次。
- 定向 `KiyoriSettingsPagesTest` `18/18` 与 `compileDebugKotlin` 同一命令在 `1m 13s` 内成功；AI 设置变更
  Kotlin 文件无 `Toast` / `CustomScaffold`，根级 `OPEN_PERSONA_GENERATION` 无引用。
- Debug 构建 `BUILD SUCCESSFUL in 2m 45s`，`232` 个任务中 `22` 个 executed、`210` 个
  up-to-date；APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`472726918` bytes；
  构建主机记录的产物时间为 `2026-08-21 07:05:48 +08:00`，仅用于产物识别，不用于判定当前
  会话日期；SHA-256 为
  `E8908268C207EF71FF397947DB2C7C8DF9D2531B55E1EF6FE90B70681E4F27FA`。
- APK 身份 `com.kiyori / 0.1.0 (45) / min 26 / target 34 / compile 37`，唯一 launcher、
  Android Debug V2 单 signer、`zipalign -c -P 16 -v 4`、`5504` 个无重复 ZIP entry、`44`
  个 DEX、arm64-only、`51` 个无重复 native basename、关键 runtime、`libsudo.so` 缺失均通过。
  独立 native 审计为 `52` 个 ELF64/AArch64、`153` 个 `PT_LOAD`，分布
  `0x4000 × 151 / 0x10000 × 2`。

## 2026-08-20 文件管理器与法律文档入口收口

状态：`LOCAL DELIVERY VALIDATED / DEVICE REVERIFY PENDING`。
本增量取代下方“文件管理器为空动作”和“更多功能只有权限”的较早基线，其余四组三项设置矩阵、
Settings Surface、来源会话与唯一状态 owner 合同继续有效。

### 入口与 owner

- 设置首页“文件管理器”和文件管理首页“手机存储”都打开
  `KiyoriShellChild.FILE_MANAGER`，直接复用现有 `FileManagerScreen`、
  `FileManagerViewModel` 与 AITool 文件操作链，不创建文件设置镜像页。
- Settings 来源打开文件管理器时，`KiyoriSettingsNavigationState` 继续保存在同一个
  `KiyoriShellState`；child 前景期间不组合 Settings surface，关闭后恢复原 route、来源与 Back 链。
- `FileManagerScreen` 自身绘制全尺寸不透明页面背景，内容统一消费
  `WindowInsets.safeDrawing`；Shell child 仍保持 edge-to-edge 背景，但工具栏和文件内容不会进入
  状态栏、显示 cutout 或导航栏区域。
- “更多功能”新增“应用与隐私 / 用户协议与隐私政策”，通过
  `KiyoriSettingsRoute.AGREEMENT` 只读复用当前协议版本、用户协议与隐私政策；首次启动同意状态
  和协议版本不在本增量中修改。
- `KiyoriLegalDocumentsScreen` 自身拥有全尺寸不透明背景与 `safeDrawing` 内容边界，概览页和
  两份正文共享同一安全区；Settings Home 不再从法律文档页面的透明区域透出。
- `toolbox.file_manager` 与 `toolbox.agreement` 不再属于可见 `NavigationSurface.TOOLBOX`；
  动态 ToolPkg 工具条目继续由唯一导航注册表投影。
- Settings-owned Operit route 使用直接替换时，`AppContent` 将该无 crossfade 策略保持到下一次
  真实 route 变化，并绕过 alpha tween：当前目标屏幕直接绘制为 `1f`，所有保活非当前屏幕直接
  绘制为 `0f`；AI Home 的对话与内部抽屉状态继续保活，但不能成为权限页首帧。

### 验收

1. [DONE] 核对未发布边界、现有协议/文件 owner、Settings session、Shell child 与 Toolbox 注册表。
2. [DONE] 清理两个 Toolbox host entry，接入 `AGREEMENT` route 和共享 `FILE_MANAGER` child。
3. [DONE] 修正 Settings overlay 与 child 同层遮挡、Back 抢占和文件管理顶栏重复返回动作。
4. [DONE] 根据设备截图修正第二层根因：法律文档/File Manager 根页面安全区与不透明背景，
   以及 `AppContent` 无 crossfade 时保活非当前屏幕错误变为不透明的问题。
5. [DONE] 增加设置矩阵、页面根源码合同、缓存屏幕透明度、Toolbox 零引用、route round-trip、
   save/restore 和来源恢复回归。
6. [DONE] 同步正式架构/TODO，复跑 architecture、formal readiness、Markdown parser 与差异检查。
7. [DONE] 串行构建并核验新的最终 Debug APK。
8. [DONE] 审计 32 文件候选树、敏感内容、构建产物、子模块与远端分歧；最终 commit/push
   结果以收尾 Git 和远端 ref 证据为准。
9. [PENDING DEVICE] 目标设备使用新 APK 验证法律文档逐级 Back、文件管理器页面返回/目录上移、
   Settings 来源恢复、权限页首帧和真实文件操作。

### 首轮本地验证证据（已被设备回归取代）

- `CharacterSelectorVisualContractTest` `4/4`、`KiyoriSettingsPagesTest` `14/14`、
  `KiyoriShellStateTest` `74/74`、`KiyoriDesignThemeTest` `13/13`，合计 `105/105`，
  零失败、零错误、零跳过。
- architecture `PASS (phase=m03)`、architecture 单元测试 `109/109`、
  `check_formal_readiness.py --require-main`、315 份工作树 Markdown 本地链接和
  `git diff --check` 通过。
- 最终 `:app:assembleDebug --no-daemon --console=plain` 在 `1m 48s` 内通过，
  `232` 个任务中 `22 executed / 210 up-to-date`；`verifySingleDebugLauncher` 与
  `verifyDebugPlayerRuntimePackaging` 通过。
- `app/build/outputs/apk/debug/app-debug.apk` 写入于
  `2026-08-21 01:20:27 +08:00`，大小 `472649202` bytes，SHA-256
  `CCBBE04F74981189BF47BFEEBA158E4411BC983972D1E072BC088487D604E237`；
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，唯一 launcher、仅 `arm64-v8a`、
  Android Debug V2 单 signer 与 `zipalign -c -P 16 -v 4` 通过。
- 未安装 APK、未操作设备；本增量终态保持 `verification_pending`。

### 2026-08-20 设备回归修订后的最终本地证据

- 设备截图证明原协议页同时暴露 Settings Home，且顶栏进入状态栏；权限页首帧同时暴露保活的
  AI 对话历史抽屉。原自动测试只证明 route/presentation 状态互斥，没有覆盖页面根绘制与缓存
  屏幕实际透明度。
- 修订后定向 JVM 已通过：`KiyoriSettingsTransitionPolicyTest` `8/8`、
  `KiyoriSettingsPagesTest` `15/15`、`KiyoriShellStateTest` `74/74`、
  `CharacterSelectorVisualContractTest` `4/4`、`KiyoriDesignThemeTest` `13/13`，
  合计 `114/114`，零失败、零错误、零跳过。
- architecture 单元测试 `109/109`、architecture boundary `PASS (phase=m03)`、formal
  readiness、Markdown parser `7/7` 与 `git diff --check` 已通过。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 47s`，`232` 个任务中 `22 executed / 210 up-to-date`；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过。
- 最终 `app/build/outputs/apk/debug/app-debug.apk` 写入于
  `2026-08-21 02:11:54 +08:00`，大小 `472649202` bytes，SHA-256
  `3CDD831C5471E9755D67D759EDAE6D3FBA431CF5A4696C1DDCC2E772FC6C34D4`；
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，唯一 Launcher、仅 `arm64-v8a`、
  `51` 个 `.so`、`5504` 个无重复 ZIP entry、Android Debug V2 单 signer 和
  `zipalign -c -P 16 -v 4` 均通过。
- 32 个允许文件精确暂存，未暂存/未跟踪、敏感形状、大 blob、文件模式和 gitlink 异常均为零；
  候选 Markdown 为 `errors=0 / warnings=0`。
- 未安装 APK、未操作设备；设备复测继续保持 `verification_pending`。

## 2026-08-20 设置首页四组三项重排与子页面统一优化

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE VERIFICATION PENDING`。
本轮使用本文件作为设置专项唯一状态载体；当前 `main` 工作区在设计开始时干净，
Kiyori 依据 `CONTEXT.md` 和 `README.md` 尚未发布，因此旧的设置首页信息架构可以彻底清理，
不保留旧入口兼容层或并行导航路径。

### 目标与非目标

目标是把设置首页从旧的 `4/4/4/4` 十六项收敛为四组各三项，并让仍然存在的设置详情统一使用
Kiyori Settings Surface 的折叠标题、分组说明、圆角卡片、双行行项目、细分隔线、选择面板和
来源保持型 Back 链。

从上到下四组固定为：

1. `我的账号 / AI助手 / 小程序`
2. `网页浏览器 / 文件下载器 / 文件管理器`
3. `视频播放器 / 音乐播放器 / 文档阅读器`
4. `界面定制 / 数据备份 / 更多功能`

本节记录本日较早基线；其中“文件管理器为空动作”已由上方增量取代。本轮原先保留
`小程序 / 文件管理器 / 音乐播放器 / 文档阅读器` 的诚实空入口语义，不凭空创建
没有 owner 的业务页面；“设置子页面 UI 优化”覆盖已有真实详情页以及本轮新增的 TTS/STT 详情页，
不把空入口伪装成已实现功能。

本轮不改变 AI、浏览器、下载、播放器、备份、账号、语音运行时或广告拦截器的业务语义、持久化
格式、协议标识、Browser Runtime、ToolPkg/MCP 标识和外部兼容边界；不执行设备安装、ADB、
模拟器或真机操作。

### 入口与状态所有权合同

| 首页入口 | 进入方式 | 唯一 owner / 处理 |
| --- | --- | --- |
| 我的账号 | `RouteEntrySource.KIYORI_SETTINGS` 进入账号根 | `GitHubAuthPreferences` 与现有账号页 |
| AI助手 | `RouteEntrySource.KIYORI_SETTINGS` 进入 AI 根 | 现有 AI 设置页；新增 TTS、STT 两个独立子项 |
| 小程序 | 空动作 | 底部小程序产品域尚无设置 owner，不接入 AI 包管理或市场 |
| 网页浏览器 | `KiyoriSettingsRoute.BROWSER` | 现有 Browser 设置；广告拦截保留在“内容过滤”组 |
| 文件下载器 | `KiyoriSettingsRoute.DOWNLOAD` | `BrowserDownloadSettingsStore` 与现有下载页 |
| 文件管理器 | `KiyoriShellChild.FILE_MANAGER` | 与文件管理首页手机存储共用现有 `FileManagerScreen` / `FileManagerViewModel` owner |
| 视频播放器 | `KiyoriSettingsRoute.PLAYER` | `PlayerSettingsStore` 与现有播放器设置页 |
| 音乐播放器 | 空动作 | 音乐播放产品域尚无独立设置 owner |
| 文档阅读器 | 空动作 | 文档阅读产品域尚无独立设置 owner |
| 界面定制 | `RouteEntrySource.KIYORI_SETTINGS` 进入界面根 | 现有语言、主题、全局显示和布局设置 |
| 数据备份 | `RouteEntrySource.KIYORI_SETTINGS` 进入数据根 | 现有备份与聊天历史 owner |
| 更多功能 | `KiyoriSettingsRoute.MORE_FEATURES` | 只读法律文档 route 与现有系统能力/权限 owner |

“广告拦截器”不再是首页入口，也不从 `KiyoriSettingsRoute` 中删除其内部规则子路由：
`AD_BLOCK_OVERVIEW` 及其子页只能由 `网页浏览器 → 内容过滤 → 广告拦截器管理` 打开。
这样删除的是错误的信息架构，不是已有 `BrowserAdBlockStore` 能力或数据。

“语音服务”不再是设置根。原混合 TTS/STT 页面按能力拆成：

- `文本转语音`：复用 `SpeechServicesPreferences` 的 TTS 配置、清洗规则、音色和测试能力；
- `语音转文本`：复用同一 preferences owner 的 STT 引擎与 HTTP 配置。

两个页面分别挂在 `AI助手` 的“语音与交互”分组下，使用设置来源为
`RouteEntrySource.KIYORI_SETTINGS` 的独立 `Screen` route；工具箱中的 TTS/STT 工具入口继续保留，
但不再被当作设置页。

### 统一视觉与交互合同

- 首页保持设置专用浅深主题、顶部快捷主题菜单和四组卡片，但卡片固定为三行，删除旧入口对应的
  图标、色板、回调和不可达动作。
- 首页保留 12 个互不重复的图标和浅深主题低饱和 icon/container 色对；色板不改变业务页面的
  `KiyoriSemanticTone` 语义。
- 所有真实详情页继续使用 `KiyoriCollapsingSettingsPage`：状态栏下展开标题、滚动折叠为固定标题、
  页面背景/卡片层级统一、导航图标统一、底部安全区统一。
- 所有分组统一使用“分组标题 + 一句职责说明 + `16dp` 圆角卡片”；行项目统一双行文案、
  `34dp` 图标容器、`16dp` 垂直节奏、`0.6dp` 分隔线和统一右侧值/箭头/开关。
- 复杂表单（TTS/STT、播放器、下载器、浏览器）只保留一层页面标题，不叠加旧式 Embedded
  Settings Top Bar；选择型配置继续复用统一的 `26dp` 圆角选择面板。
- 所有设置页的标题、Back、系统 Back、来源恢复和 Operit route 详情继续由现有
  `KiyoriSettingsNavigationState`、`LocalKiyoriEmbeddedSettingsNavigation` 和 Shell owner
  共同持有，禁止页面自行创建第二套路由或镜像偏好。
- 错误、加载、禁用和空状态必须是明确的当前状态文案；不引入兜底导航、隐藏旧入口或伪造动态数据。

### 实施与验证计划

1. [DONE] 读取项目规则、正式开发准备清单、设置架构、当前 Git 基线和未发布兼容边界。
2. [DONE] 研究设置首页、AI/语音/浏览器/广告拦截详情页、Screen route、Settings session、
   Back owner、持久化 owner 和现有回归测试。
3. [DONE] 冻结上述 3/3/3/3 入口矩阵、AI TTS/STT 归属、Browser 广告拦截归属和统一视觉合同。
4. [DONE] 更新首页、色板、AI 组、TTS/STT 页面与 Screen route；移除旧语音根和首页旧回调，
   保留工具箱入口和底层语音协议。
5. [DONE] 逐页收敛账号、AI、TTS、STT、浏览器、下载器、播放器、广告拦截、界面、备份、
   更多功能的旧式间距、顶栏和状态文案。
6. [DONE] 更新 `CONTEXT.md`、相关正式架构文档、当前专项证据与引用；增加首页矩阵、路由、
   旧入口零引用、色板唯一性和广告拦截归属回归测试。
7. [DONE] 串行执行定向 JVM/Kotlin 编译、`check_formal_readiness.py --require-main`、
   必要 architecture/Markdown/资源检查、`git diff --check`。
8. [DONE] 串行执行 `./gradlew :app:assembleDebug --no-daemon --console=plain`，核验
   `app/build/outputs/apk/debug/app-debug.apk` 的身份、时间、大小、哈希、签名、对齐和 native
   产物。
9. [DONE] 精确审计候选树、敏感内容、构建产物、子模块和远端竞争；交付提交与远端状态以 Git
   历史和本轮最终报告为准。
10. [PENDING] 设备视觉、触控、登录/语音表单、来源返回和跨屏验收保持
    `verification_pending`。

### 风险与完成标准

- 最大风险是拆分语音设置时误复制 `SpeechServicesPreferences` 或丢失 TTS/STT 自动保存语义；
  完成标准是两个新页面均直接读写同一 owner，并覆盖真实配置 round-trip。
- 第二个风险是设置 route 与 Operit route presentation 同事务变化造成残影或错误 Back；
  完成标准是新旧设置来源都能按 `AI/Browser → 设置首页 → 详情 → 设置首页 → 原页面`
  逐级恢复，并且不改变 Browser Runtime 或 AI Host。
- 第三个风险是首页入口删除后仍有旧回调、旧图标色板或文档合同残留；完成标准是代码/测试/文档
  反向搜索只在底层语音协议、日志存储或历史事实中保留相应术语，不在当前 Settings Home
  入口合同中保留旧项。

### 当前自动验证证据

- `:app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL`，`83` 个任务。
- 定向设置、Shell 与主题 JVM：`KiyoriSettingsPagesTest 14/14`、
  `KiyoriShellStateTest 72/72`、`KiyoriDesignThemeTest 13/13`，合计 `99/99`，零失败、错误或跳过。
- architecture Python 单元测试 `109/109`；`check_architecture_boundaries.py --require-main`：
  `PASS (phase=m03)`。三个受保护源码快照只同步本轮删除的旧语音/首页广告拦截回调，没有扩大
  import allowlist。
- `check_formal_readiness.py --require-main`、七份 `strings.xml` 解析、315 份 Markdown 本地链接、
  旧语音根/旧首页色板零引用和 `git diff --check` 已通过。
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 49s`，`232` 个任务；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过。
- 最终 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`472649202` bytes，SHA-256
  `6F8255D7D7A35D29012F30E06A2C109375DD7CF71E81B1E63CBD90C143AE2640`；包名 `com.kiyori`，
  版本 `0.1.0 (45)`，minSdk `26`、targetSdk `34`、compileSdk `37`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`。
- Android Debug V2 单 signer 与 `zipalign -c -P 16 -v 4` 通过；APK 仅含 `arm64-v8a`，
  `44` 个 DEX、`51` 个 `.so` 且 basename 零重复。加上 `assets/operit_shell_exec` 共审计
  `52` 个 ELF64/AArch64，`153` 个 `PT_LOAD` 为 `0x4000 × 151 / 0x10000 × 2`，零低于
  `0x4000`；两个非播放器 owner 的 `$ORIGIN` RUNPATH 与现有正式合同一致，本轮没有新增。

## 历史基线（已由 2026-08-20 方案取代）

以下内容保留为旧方案的实施证据和迁移背景，不再作为当前入口、路由或视觉合同。

设置首页继续保持固定 `4/4/4/4` 十六入口，但第一组不再保留没有真实页面的“剪贴板口令”和
独立“小程序订阅”。本阶段把原综合 AI 设置按实际状态所有者拆分，并让新增设置根页统一使用
文件下载器设置页已经确立的视觉与交互组件。

第一组从上到下固定为：

1. 我的账号
2. AI助手
3. 语音服务
4. 小程序

第二组从上到下固定为：

1. 网页浏览器
2. 视频播放器
3. 音乐播放器
4. 文档阅读器

最后一组从上到下固定为：

1. 界面定制
2. 数据备份
3. 开发手册
4. 更多功能

## 信息归属

| 设置根 | 内容 | 唯一状态所有者 |
| --- | --- | --- |
| 我的账号 | 进入标题同名的账号根页；GitHub 登录状态、登录与退出 | `GitHubAuthPreferences` |
| AI助手 | 进入现有“AI 助手”根页；用户偏好、模型/API、功能模型、提示词、人设、分句模式、上下文总结、AI 工具授权、Token 统计、外部 HTTP 对话 | 原有 AI preference、repository 与 `ToolPermissionSystem` |
| 语音服务 | TTS、STT、语音测试入口 | `SpeechServicesPreferences` 与现有语音 runtime |
| 小程序 | 本轮只保留 Settings Home 空按钮，等待底部第三个“小程序”产品域建立真实管理页 | 尚未建立 |
| 界面定制 | 语言、主题与外观、全局显示、布局调整 | 原有显示与主题 preference |
| 数据备份 | 进入现有数据根页；聊天及记忆数据备份、聊天历史管理 | 原有备份与聊天 repository |
| 开发手册 | 保留开发文档产品入口，不建立第二套终端、工具箱或开发模式状态 | 尚未建立 |
| 更多功能 | 统一设置风格的系统能力子页；“权限”进入原生权限与设备能力页 | 首次启动与 Settings 共享的 `KiyoriPermissionSnapshot` 和授权动作 |
| 网页浏览器 | 普通网站 Cookie 清理 | `CookiePrivacyManager` |

移动入口只改变信息架构和导航，不复制、迁移或改写任何持久化状态。“小程序”严禁连接
AI 包管理、脚本包、ToolPkg、插件市场或 AI 抽屉路由；小程序订阅能力以后如有真实 owner，应
进入底部第三个“小程序”产品域内部，不重新占用设置首页入口。

## 视觉合同

- AI助手、我的账号、语音服务、界面定制和数据备份继续进入现有
  `KiyoriCollapsingSettingsPage`
- 分组统一使用标题、说明、`16dp` 白色圆角卡片、双行设置项和 `0.6dp` 分隔线
- 可选择项继续使用文件下载器设置页的 `26dp` 圆角底部面板
- 模态 AI 抽屉底部显示通用“设置”，启动 `KiyoriSettingsOrigin.AI_HOST` 的来源保持型设置会话；
  AI 助手详情由设置首页同名入口进入并显示返回
- 账号、语音、界面和备份根页使用 `RouteEntrySource.KIYORI_SETTINGS`，并携带活动设置
  `navigationContextId/sessionId`；根页面 Back 恢复同一 Settings Home，内部子页 Back 先返回
  对应设置根，来源保持会话最后再返回原浏览器或 AI 页面
- 小程序和开发手册保持空动作；更多功能进入 `KiyoriSettingsRoute.MORE_FEATURES`，其中“权限”
  在当前 settings `sessionId` 内压入 `KiyoriSettingsRoute.PERMISSIONS`，与首次启动共享 21 项
  权限目录、真实 snapshot、metadata 和授权动作，不建立第二份权限结果
- 首页 16 个入口分别使用 16 个不同图标和 16 组固定浅深色图标容器；详情页继续使用全应用
  `KiyoriSemanticTone` 身份，但通过 Settings Surface 专用低饱和浅深色对渲染，不把首页专用
  色板扩散到业务状态语义，也不改变文件管理、工具箱等非设置界面
- 顶栏第 4 个按钮在自身下方展开“跟随系统 / 浅色模式 / 深色模式”，直接写入唯一
  `UserPreferencesManager` 主题 owner；菜单固定宽度为 `156dp`，按钮按当前有效主题显示太阳或月亮
- 网页浏览器、视频播放器和文件下载器详情标题直接复用设置首页同名文案，不再追加“设置”
- 浏览器菜单与 AI 抽屉共用 `KiyoriSettingsNavigationState`，不切换到底部设置主目的地；
  覆盖式设置首页隐藏底部五入口，分类和子页按 `KiyoriSettingsRoute` 栈逐级返回

## 串行实施

1. [DONE] 更新 Settings Home 入口、动作与路由来源
2. [DONE] 把旧综合 AI 设置收敛为 AI 助手专属分组
3. [DONE] 新增账号与连接、界面定制、数据备份与同步设置根
4. [DONE] 把语音服务外壳统一为文件下载器设置页风格
5. [DONE] 把 Cookie 清理迁入网页浏览器“网站权限与数据”
6. [DONE] 更新语义文档、README、定向测试并执行正式门禁和 Debug APK 构建核验
7. [DONE] 将设置首页短标签收敛为两组固定四项，并建立 16 图标、16 色的一一对应合同
8. [DONE] 将账号首页与详情标题统一为“我的账号”，将数据根标题统一为“数据备份”，并新增
   主题快捷菜单与 Settings Surface 详情图标色板
9. [DONE] 将第二组末项改为“文档阅读器”，统一浏览器/播放器/下载器同名标题，缩窄
   主题菜单，并建立浏览器与 AI 来源保持型设置首页返回链
10. [DONE] 用 `KiyoriSettingsNavigationState` 和 capability-level `KiyoriSettingsRoute`
    替代设置类 `KiyoriShellChild`、`childBackTarget` 与浏览器局部子页状态；Browser/AI 来源最终
    恢复原页面和原路由栈
11. [DONE] 新增“更多功能”设置子页和“权限”导航项，复用原设备能力 owner，并建立
    权限页 -> 更多功能 -> 设置首页的逐级 Back 合同

## 验收边界

自动测试和 Debug APK 不能证明手机、平板、横屏下的折叠标题、长表单、登录弹窗、返回手势和
输入法体验。实现与本地构建完成后仍需目标设备验收。

## 本地验证证据

- `KiyoriSettingsPagesTest` 为 `10/10`，`KiyoriShellStateTest` 为 `42/42`，零失败、零错误、
  零跳过；覆盖设置首页顺序、小程序空动作、拆分后的能力归属、浏览器 Cookie 行与新增根路由恢复
- `python -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- `git diff --check` 通过；工作树仅保留本轮未提交修改
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 在 `94.6s` 内完成，
  `233` 个任务，`BUILD SUCCESSFUL`
- Debug APK：
  `app/build/outputs/apk/debug/app-debug.apk`，`482597235` bytes，SHA-256
  `B69DBAD0C2A69733D67759F54FF9DE68A55BE83C78D012D9A72108405A406315`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、`arm64-v8a`；Android Debug v2
  签名与 `zipalign -c -P 16 -v 4` 验证通过
- 未安装 APK、未操作设备；折叠标题、长语音表单、GitHub 登录弹窗、来源相关返回和横竖屏视觉
  保持 `verification_pending`

### 2026-08-14 增量验证

- 设置/Shell、浏览器菜单/布局和外部入口定向 JVM 共 `82/82`，architecture boundary
  `107/107`，Kotlin 编译、formal readiness、七语种 XML、worktree Markdown 链接和
  `git diff --check` 均通过
- Debug 构建为 `232` 个任务、`22 executed / 210 up-to-date`；APK 为 `471035851` bytes，
  SHA-256 `28BFBC295AEA0433C279FC7EFCF9BE6FF37468E306B5E3C33CCBF4384CAD821C`
- APK 身份为 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，Android Debug V2 单 signer，
  16 KB ZIP 对齐通过；目标设备视觉与交互仍保持 `verification_pending`

### 2026-08-20 权限与设备能力中心增量

- 设置首页“更多功能”已从空动作改为 `KiyoriSettingsRoute.MORE_FEATURES`，使用与其他设置详情
  一致的折叠标题、系统能力分组卡和双行导航项
- “权限”通过当前 settings `sessionId` 打开 `KiyoriSettingsRoute.PERMISSIONS`；权限页 Back
  恢复更多功能，再 Back 返回设置首页，不进入 App Router
- 权限页与首次启动共享 21 项真实设备权限，按应用权限、系统访问和高级设备能力分组；提供总览、
  原位刷新、逐项动作和依次处理待授权项，不启动 Terminal、Node、Python 或 MCP 环境检查
- `Screen.ShizukuCommands / ShizukuDemoScreen` 保留为独立执行通道与开发诊断页面，不再承担
  Settings 权限首页职责
- 本轮定向 JVM 五套测试 `119/119`：`KiyoriSettingsTransitionPolicyTest` `9/9`、
  `KiyoriSettingsPagesTest` `16/16`、`KiyoriShellStateTest` `74/74`、
  `KiyoriOnboardingContractTest` `15/15`、`KiyoriOnboardingPermissionsTest` `5/5`
- architecture `PASS (phase=m03)`、architecture 单元测试 `109/109`、
  `check_formal_readiness.py --require-main`、最终 package 状态的 Kotlin 编译和
  `git diff --check` 通过；设备首帧、授权动作和系统页返回仍保持 `verification_pending`
- 规定 Debug 构建为 `BUILD SUCCESSFUL in 2m 2s`；最终 APK 为
  `app/build/outputs/apk/debug/app-debug.apk`，`472553854` bytes，SHA-256
  `8C08C8D7150BBDB56EE1018BFE1C24FDB07C70F453A3F0AB9ABA74DF267A87E1`，
  包/版本/SDK、唯一 launcher、arm64-only、Android Debug V2 单 signer 与 16 KB ZIP 对齐通过
- 未安装或操作设备；真实点击、逐级 Back 与浅深主题视觉保持 `verification_pending`
