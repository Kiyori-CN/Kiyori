---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
baseline: c036a03e
---

# 设置页信息架构与统一视觉

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

本轮保留 `小程序 / 文件管理器 / 音乐播放器 / 文档阅读器` 的当前诚实空入口语义，不凭空创建
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
| 文件管理器 | 空动作 | 当前文件管理首页 owner；本轮不虚构文件设置子页 |
| 视频播放器 | `KiyoriSettingsRoute.PLAYER` | `PlayerSettingsStore` 与现有播放器设置页 |
| 音乐播放器 | 空动作 | 音乐播放产品域尚无独立设置 owner |
| 文档阅读器 | 空动作 | 文档阅读产品域尚无独立设置 owner |
| 界面定制 | `RouteEntrySource.KIYORI_SETTINGS` 进入界面根 | 现有语言、主题、全局显示和布局设置 |
| 数据备份 | `RouteEntrySource.KIYORI_SETTINGS` 进入数据根 | 现有备份与聊天历史 owner |
| 更多功能 | `KiyoriSettingsRoute.MORE_FEATURES` | 现有系统能力/权限 owner |

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
| 更多功能 | 统一设置风格的系统能力子页；“权限”进入原设备能力页面 | `Screen.ShizukuCommands` 及其现有权限 owner |
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
  使用当前 settings `sessionId` 和 `RouteEntrySource.KIYORI_SETTINGS` 打开原
  `Screen.ShizukuCommands`，不建立第二权限页面或状态
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

### 2026-08-19 更多功能与权限入口增量

- 设置首页“更多功能”已从空动作改为 `KiyoriSettingsRoute.MORE_FEATURES`，使用与其他设置详情
  一致的折叠标题、系统能力分组卡和双行导航项
- “权限”通过当前 settings `sessionId` 与 `RouteEntrySource.KIYORI_SETTINGS` 打开原
  `Screen.ShizukuCommands`；权限根页 Back 恢复更多功能，再 Back 返回设置首页
- `KiyoriSettingsPagesTest` `14/14`、`KiyoriShellStateTest` `71/71`，architecture
  `PASS (phase=m03)`、architecture 单元测试 `109/109`、formal readiness 和
  `git diff --check` 通过
- 规定 Debug 构建为 `BUILD SUCCESSFUL in 2m 2s`；最终 APK 为
  `app/build/outputs/apk/debug/app-debug.apk`，`472553854` bytes，SHA-256
  `8C08C8D7150BBDB56EE1018BFE1C24FDB07C70F453A3F0AB9ABA74DF267A87E1`，
  包/版本/SDK、唯一 launcher、arm64-only、Android Debug V2 单 signer 与 16 KB ZIP 对齐通过
- 未安装或操作设备；真实点击、逐级 Back 与浅深主题视觉保持 `verification_pending`
