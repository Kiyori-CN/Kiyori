---
For_Agent: 对项目大规模动工前按本规范协作
---

## 2026-08-10 负一屏历史网页与媒体点击修复

状态：网页和媒体入口的根因修复、本地自动验证、Debug APK 构建和静态产物核验已完成；
目标设备上的冷启动、同进程播放和 Back 返回仍保持 `verification_pending`。

细化计划：

1. [DONE] 修正网页、小说和其他 URL 条目的 Shell 状态所有权，避免历史抽屉用旧状态覆盖
   Browser Home 导航
2. [DONE] 负一屏视频和音乐条目直接启动唯一 `PlayerActivity`，Browser Home 内入口继续消费
   原有一次性全屏 presentation 请求
3. [DONE] 新增独立 `HISTORY_REPLAY` 来源，从普通 Profile 的持久 Cookie owner 和当前浏览器
   设置重建在线请求身份，移除冷启动对活动 WebSession 的依赖
4. [DONE] 保持 Browser candidate 的真实 session、下载和悬浮返回语义；历史来源不伪装成
   candidate，本地历史继续使用现有队列解析
5. [DONE] 让 Browser candidate 按来源 Profile 决定是否写共享历史，无痕媒体不持久化
6. [DONE] 增加网页状态所有权、历史来源、source session、Profile 持久化和全屏返回策略测试
7. [DONE] 串行执行定向 JVM、formal readiness、`git diff --check`、Debug APK 构建和产物核验
8. [PENDING] 在目标设备复测网页直达、冷启动和同进程在线视频、本地视频及 Back 返回

本轮不创建隐藏 WebView、第二 PlayerSession、第二播放器、下载旁路或持久化请求头，不安装 APK，
不执行 ADB/MuMu/真机操作，不提交，不推送。

本地验证证据：

- `KiyoriShellStateTest`、`BrowserMediaCandidatePolicyTest`、`WebSessionHistoryPolicyTest`、
  `WebSessionProfilePolicyTest`、`PlayerPolicyTest` 与 `PlayerSurfaceLeasePolicyTest` 共
  `126/126` 通过，失败、错误和跳过均为 `0`
- 项目 `.venv` 的 formal readiness 通过；完整 `git diff --check` 无 whitespace error，
  仅报告其他既有脏文件的 CRLF 转换警告
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 43s`，
  `238` 个任务中 `28` 个执行、`210` 个为最新状态；`verifySingleDebugLauncher` 与
  `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `475435609` 字节，
  SHA-256 `BDDD1DFB558BE6E9BE8A5AB8B133917A15FA17C66A081C4B0A446AC7EF61ECB3`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min/target/compile SDK `26 / 34 / 37`、仅
  `arm64-v8a`；Android Debug v2 单 signer 签名和 `zipalign -c -P 16 -v 4` 验证通过

## 2026-08-10 Markdown/LaTeX 公式兼容性与化学渲染

状态：源码修复、自动化回归用例、文档、本地自动化验证和 Debug APK 构建已完成；
目标设备上的公式视觉、横向拖动与纵向滚动协同、流式输入时序和不同屏宽验收待完成，
交付状态保持 `verification_pending`。
本轮继续复用唯一 Compose/JLaTeXMath 公式链路，不新增 WebView、KaTeX、MathJax 或第二套公式状态源。

细化计划与验收矩阵见：

- [`markdown_latex_rendering_compatibility/`](markdown_latex_rendering_compatibility/index.md)

本轮并行交付边界：

- 本文件下方已有的 Browser 工具视口、链接导航、控制台与 `run_code` 契约修复段落
- 不安装 APK、不执行 ADB/MuMu/真机操作、不提交、不推送

## 2026-08-10 Browser 工具视口、链接导航、控制台与 run_code 契约修复

状态：根因定位、源码修复、自动化回归、本地验证和 Debug APK 构建已完成；目标设备验收
待完成，交付状态保持 `verification_pending`。继续复用唯一
`StandardBrowserSessionTools`、每个 `WebSession` 的真实 WebView、现有 Browser Host 和
userscript runtime；不创建第二套浏览器、标签注册表、页面代理或 console owner。

已确认根因：

- `browser_resize` 只保存请求尺寸并调用 `zoomBy` 模拟宽度，没有改变活动 WebView 的真实布局；
  Browser Home 未挂载时，同一个 WebView 又被放入永久 `1×1` 的透明 background anchor
- `WebSessionBrowserHost.setViewportSize` 把请求宽高按 `240dp / 320dp` 强制下限处理，导致
  `900×600` 在当前设备密度下变成状态中的 `900×1120`
- 普通左键 ref 点击优先向 WebView 派发原生触摸；当 WebView 为 `1×1` 时，页面元素坐标被压到
  `(1,1)`，同时 click settlement 不等待链接应触发的导航，也不报告导航超时
- userscript 的 page 与 isolated 两套 document-start bootstrap 在总授权关闭时分别收到一次
  `userscript_permission_required`，bootstrap 又把内部错误镜像到页面 `console.error`
- `browser_run_code` 的 `page` 仅实现未声明清楚的局部方法；`setContent`、`once` 等缺失方法
  直接暴露 JavaScript `TypeError`，与包级“严格对齐”描述不一致

细化计划：

1. [DONE] 核对 `AGENTS.md`、正式开发准备清单、Git 基线和唯一 Browser Runtime 所有权
2. [DONE] 追踪 resize、页面状态、background anchor、WebView 布局、点击坐标、导航等待、
   userscript bootstrap、console 采集和 `run_code` Page 代理的完整调用链
3. [DONE] 把 viewport 改为每个 session 的真实 CSS 布局合同；移除 dp 强制放大和
   zoom 模拟，只在 Host 边界按 density 转换物理布局尺寸，切换标签时恢复各自尺寸，页面状态读取实际 DOM viewport
4. [DONE] 保留真实点击语义，修正 CSS 到 WebView 坐标映射；链接点击等待导航或新标签，
   并明确区分导航完成、被对话框暂停和超时/阻止
5. [DONE] 总授权关闭时让 userscript bootstrap 返回空脚本集合；内部 bridge/runtime
   诊断不再写入页面 console，已授权脚本在权限被撤销后仍获得结构化权限错误
6. [DONE] 为 `run_code` 增加 `page.setContent`、`page.on/once('dialog')` 及
   alert/confirm/prompt dialog 对象；其他未知 Page API 返回 `Unsupported Playwright API`
7. [DONE] 扩展 browser Android JS 回归套件，覆盖串行/并发 goto+resize、标签独立尺寸、
   页面布局 viewport、按钮与链接点击、导航超时、console 隔离、dialog、fetch/XHR 和多标签绑定
8. [DONE] 增加 viewport、点击导航、userscript 权限和 `run_code` 方法契约 JVM 测试，
   同步 `README.md`、`CONTEXT.md` 和 browser 包双语描述
9. [DONE] 串行执行定向 JVM 测试、AndroidTest 编译、formal readiness、Markdown 链接与差异检查，
   最后构建并核验 Debug APK

本地验证证据：

- Browser 与 Markdown/LaTeX 联合定向 JVM 共 `8` 个测试类、`63/63` 通过，失败、错误和跳过均为 `0`
- `:app:compileDebugAndroidTestKotlin`：`BUILD SUCCESSFUL`，`146` 个任务；Browser Android JS
  回归套件已写入并通过编译/打包，但尚未在目标设备运行
- 项目 `.venv` 的 formal readiness、`git diff --check` 和本轮 `9` 份 Markdown 本地链接检查通过
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`，`238` 个任务；
  Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，SHA-256
  `FA53014E1D66469723AD99F72CC85DC39B22A9C967983B35612A4C725849ED04`
- APK 静态核验：`com.kiyori`、`0.1.0 (45)`、仅 `arm64-v8a`、Android Debug v2 签名通过、
  16 KB ZIP 对齐验证通过

验收边界：

- 不通过伪造页面状态掩盖真实 WebView 仍为 `1×1`
- 不把链接点击改成读取 `href` 后直接调用导航
- 不新增 fallback、第二 Browser Runtime、第二 WebView 或第二 userscript/console 状态源
- 真实 viewport、跨域链接点击、WindowManager 大尺寸 background anchor、页面 console 隔离和
  `run_code` dialog 时序仍需目标设备复测
- 不安装 APK、不执行 ADB/MuMu/真机操作、不提交、不推送

## 2026-08-10 设置页主题快捷入口与负一屏网址直达

状态：本地实现、自动验证和 Debug APK 构建已完成，目标设备视觉与交互验收待完成。本轮在
既有设置首页 16 入口和主题 owner 基础上继续收口，不新增第二主题状态源、不改变已存在的
设置详情状态所有者；负一屏书签和历史 URL 进入同一 Browser Home，不创建悬浮浏览器入口。

细化计划：

1. [DONE] 核对 `AGENTS.md`、正式开发准备清单、现有脏工作树和本轮重叠差异
2. [DONE] 定位 `UserPreferencesManager` 主题 owner、设置首页第 4 个按钮和
   `BrowserPresentationCoordinator` 的负一屏入口顺序
3. [DONE] 将首页与详情页标题统一为“我的账号”“数据备份”，并调整设置首页及详情页图案配色
4. [DONE] 在第 4 个顶栏按钮正下方实现“跟随系统 / 浅色模式 / 深色模式”快捷菜单，点击即时写入
   既有主题偏好，按钮图标随有效主题显示太阳或月亮
5. [DONE] 让负一屏书签和历史中的网页 URL 先进入 Browser Home，再在可见网页宿主中导航；保留
   视频/音乐历史的播放器语义和浏览器其他入口语义
6. [DONE] 增加相关 JVM/源码合同测试，更新 `CONTEXT.md`、设置信息架构与主题视觉文档
7. [DONE] 串行执行 `:app:assembleDebug --no-daemon --console=plain` 并核验 Debug APK
8. [DONE] 审查差异、构建产物和既有工作树，完成任务日记收尾；不提交、不推送

验收边界：

- 本地自动检查和 Debug 构建不替代目标设备上的设置点击、主题切换和 Browser Home 真实导航验收
- 本轮不安装 APK、不执行 ADB/MuMu/真机操作、不提交、不推送

本地验证证据：

- 设置、设计主题和 Browser Home 导航策略 3 个 JVM 测试类共 `29/29` 通过，失败、错误和
  跳过均为 `0`
- `ci.test.test_architecture_boundaries` 共 `106/106` 通过；正式开发准备检查通过；
  `git diff --check` 无 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功完成 `238` 个任务，
  其中 `29` 个执行、`209` 个为最新状态
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`475435609` bytes，SHA-256
  `1108CDB7D0D4CFFC376585D1B141370C7D70785D7F09B0B6CF18347D24E135C8`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min/target/compile SDK `26 / 34 / 37`、唯一
  `arm64-v8a`；包含 `51` 个原生库且 basename 无重复，内置 `assets/accessibility.apk`
  恰好一份
- Android Debug v2 单 signer 签名和 `zipalign -c -P 16 -v 4` 验证通过

# TODO不误砍柴功

## 2026-08-10 Kiyori 首启收口、默认新对话与设置首页16色

状态：本地实现、自动化验证、Debug APK 构建和静态审计完成。范围仅包含首启第一页标题对齐、
六页流程静态与自动化复核、首次 AI 首页真实空白对话初始化、设置首页 16 个入口的文案/图标/
配色，以及对应文档、测试和 Debug APK。不新增设置详情页，不改变已有入口的状态所有者，
不安装 APK、不操作设备。

设计与验收合同：

1. [DONE] 仅将第一页“欢迎使用 Kiyori”居中，带眉题的第 2 至第 4 页标题继续左对齐
2. [DONE] 复核唯一 `HorizontalPager`、六页顺序、协议门禁、权限队列和授权完成直达应用
3. [DONE] 当前对话为空或已不存在时创建并选中真实空白对话；已有有效对话时尊重
   “每次启动新建空白聊天”偏好
4. [DONE] 设置首页顶部固定为“账号连接 / AI助手 / 语音服务 / 小程序”，底部固定为
   “界面定制 / 数据备份 / 开发手册 / 更多功能”
5. [DONE] 16 个入口使用 16 个互不重复的图标和 16 组功能语义配色；浅色、深色下的图标
   前景色与容器色均保持一一对应且互不重复
6. [DONE] 同步设置视觉、信息架构、首启和 AI 首页初始化文档，更新自动约束
7. [DONE] 执行定向 JVM 测试、架构门禁、正式准备、差异检查、Debug 构建和 APK 静态核验
8. [PENDING] 在目标设备验收六页视觉、左右滑动、系统授权往返、首个对话可见性和设置页配色

本地自动验证证据：

- 7 个相关 JVM 测试类共 `50/50` 通过，失败、错误和跳过均为 `0`
- `ci.test.test_architecture_boundaries` 共 `106/106` 通过；首启专属架构检查通过
- 正式开发准备检查通过，`git diff --check` 无 whitespace error，Markdown 链接单测 `7/7` 通过
- 完整 `phase=m03` 门禁仍发现工作树基线已有的市场持久化合同漂移、`AppDatabase` 受控哈希
  漂移和 `UserscriptSourceExportHelper.kt` 的 M-05E 路径消费者漂移；这些不属于本轮首启/
  设置范围
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功完成 `238` 个任务，
  其中 `29` 个执行、`209` 个为最新状态
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`475435894` bytes，SHA-256
  `ABA70E52CA7099C2C9AE644EFF5295D8670F64AF75625EE2EB97B1CC35CF762C`
- APK 为 `com.kiyori`、`45 / 0.1.0`、target 34、唯一 `arm64-v8a`；包含 `51` 个原生库，
  ZIP 路径和原生库 basename 均无重复，内置 `assets/accessibility.apk` 恰好一份
- Android Debug v2 签名和 `zipalign -c -P 16 -v 4` 验证通过

## 2026-08-10 Kiyori 首启前四页统一版式与16能力图标

状态：中文前四页本地实现、自动验证与 Debug APK 已完成，已修复最新截图中的卡片文案截断、
主视觉图标错配、图标重复和页面间卡片位置漂移；协议、权限和左右滑动流程保持不变，真机
视觉继续保持 `verification_pending`。

本轮前四页统一使用同一个展示骨架：固定主视觉槽、标题槽、介绍槽和 2×2 能力网格基线。
第一页不再显示“AI 浏览器 · 内容工作台”，标题直接贴近主视觉，并把等量纵向节奏转移到介绍
与能力网格之间，因此四页能力网格的常规坐标保持一致；标题下方介绍正文使用两个中文字符
宽度的首行缩进，大字体和极窄屏允许槽位自然增高并由页面滚动承载。

四页只定义一份 `OnboardingFeatureCard` 列表，主视觉与下方卡片共同消费同一列表。产品总览
使用 `Explore / SmartToy / Layers / Extension`，浏览与内容使用
`Language / Download / PlayCircle / MenuBook`，AI 协作使用
`AutoAwesome / RecordVoiceOver / AccountCircle / Widgets`，本地工作区使用
`Folder / Terminal / Apps / BugReport`，四页共 16 个图标互不重复。

能力卡移除固定 86dp 和两行省略限制，改为行内等高、自适应最小高度和完整正文显示；文案
继续保持中文优先，并覆盖网页、下载、视频、音乐、小说、广告拦截、AI、语音、账号、工具箱、
文件、终端、小程序和日志等 Kiyori 能力。

设计、实施步骤与验收入口：

- [`kiyori_first_run_experience/`](kiyori_first_run_experience/index.md)

## 2026-08-09 Kiyori 完整首次启动体验

状态：中文六页信息密度与视觉层级已完成 r5 精修，支持应用图标一致性与首页 UI 已完成 r6 精修，左右滑动和本地自动验证已完成；真机视觉和系统授权往返保持
`verification_pending`。

本轮彻底替换 Operit 风格的协议、宣传、权限等级和完成页流程，建立 Kiyori 自有的六页中文首次
启动体验。流程依次为产品总览、浏览器与内容工作台、AI 与连接服务、本地文件/终端/小程序工作区、双协议
确认、权限选择；第六页完成用户所选授权后直接进入应用，不再保留独立完成页。

前四页不再使用“插画 + 一段正文 + 少量标签”的稀疏宣传布局，改为紧凑插画、主题导语和
2×2 能力卡片。产品总览覆盖浏览与内容、AI 与语音、文件与工具、连接与扩展；后三张介绍页
进一步展示网页浏览器、文件下载器、视频播放器、音乐/小说/广告拦截、AI 助手、语音服务、
账号与连接、工具箱、文件管理器、终端、小程序管理和日志记录器。

六页由同一个横向 Pager 管理，主按钮、顶部返回、左右滑动、步骤进度和持久化当前页保持一致；
单次滑动最多切换一页。未同意协议时不能向前滑入权限页，但可以向右返回上一页；授权处理中锁定
滑动与返回，避免系统授权队列和可见页面分离。

每页只保留一个主操作。协议不要求打开或滚动到底，勾选同意后即可继续；权限取消运行时、特殊
访问、高级能力分区和标准、无障碍、Shizuku、Root 等等级选择，改为统一清单，由用户逐项选择
需要授权的能力。当前只维护中文默认资源，其他语言在六页中文定稿和真机视觉验收后统一处理。

无障碍条目安装的独立支持应用已改为 `Kiyori 无障碍支持`，系统服务名为
`Kiyori UI 自动化服务`，并使用 Kiyori 图标。内部包名和 AIDL action 继续保留原值，因为它们是
主应用与支持应用之间的 IPC 兼容标识，不属于用户可见品牌。

Kiyori 始终未发布，旧 PermissionGuide 页面、ViewModel、资源、重复 `setContent` 和启动时无条件
通知请求直接删除，不保留兼容开关或并行入口。协议许可事实同步修正为 GPL-3.0-or-later。

设计、实施步骤与验收入口：

- [`kiyori_first_run_experience/`](kiyori_first_run_experience/index.md)

## 2026-08-09 包管理顶栏、固定搜索与市场安装刷新

状态：本地实现、自动验证和 Debug APK 已完成；目标设备交互保持 `verification_pending`。

Kiyori 始终未发布，本轮直接删除包管理右下角的环境变量、市场、添加和错误浮动入口，
不保留旧布局、兼容开关或并行入口。包管理顶栏在“包管理”标题右侧按固定顺序显示
“环境变量 / 市场 / 添加 / 刷新”；搜索框单独固定在顶栏下方，四个页签固定在搜索框下方，
只有列表内容滚动。

细化步骤：

1. [DONE] 核对宿主顶栏动作绑定、页面搜索状态、四页签结构和底部浮动按钮
2. [DONE] 定位市场安装成功后页面本地包快照未更新的根因
3. [DONE] 冻结顶栏动作顺序、固定搜索/页签层级、错误提示入口和加载状态
4. [DONE] 抽取唯一包管理快照重载入口，并接通手动刷新和市场安装成功信号
5. [DONE] 删除浮动按钮与旧顶栏搜索绑定，收回列表底部遮挡预留空间
6. [DONE] 增加动作顺序、市场目录修订信号和源码合同测试
7. [DONE] 同步 README、CONTEXT、架构文档并完成正式门禁与 Debug APK 核验
8. [PENDING] 在目标设备验收窄屏顶栏、输入法、搜索、页签、自动刷新和开关触摸

设计与验收入口：

- [`package_manager_header_and_market_refresh/`](package_manager_header_and_market_refresh/index.md)

本轮本地验收证据：

- 包管理相关 JVM 测试 `19/19` 通过，失败、错误和跳过均为 `0`
- 正式开发准备门禁通过；7 个新增资源键在 7 个语言目录中全部存在且 XML 可解析
- 当前工作树 8 份相关 Markdown 本地链接检查通过，`git diff --check` 无 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功完成 `238` 个任务，
  其中 `29` 个执行、`209` 个为最新状态
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`475317754` 字节，
  SHA-256 `8807B82473E086428396132915FD44D40D48ACB43C64A6AA25995A4A84C9227B`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / arm64-v8a`，Debug V2 签名和
  `zipalign -c -P 16 4` 均通过

目标设备上的真实窄屏顶栏、输入法、市场返回路径和安装后即时显示仍需单独验收。

## 2026-08-09 包管理环境变量配置抽屉

状态：首轮抽屉替换、分类视觉、紧凑密度和统一排序均已完成，目标设备交互保持
`verification_pending`。

Kiyori 始终未发布，本轮直接用可拖动底部抽屉替换包管理右下角环境变量按钮打开的旧弹窗，
不保留旧弹窗状态、兼容开关或并行入口。变量声明、读写与保存继续使用现有 `ToolPackage.env`
和 `EnvPreferences`，不改变 ToolPkg 格式、包启用校验或 MCP 环境变量页面。

细化步骤：

1. [DONE] 核对旧弹窗、环境变量 owner、包显示名和浏览器三态抽屉实现
2. [DONE] 冻结标题、搜索、横向分类、工具包折叠、固定操作区和取消/保存语义
3. [DONE] 抽取共享三态底部抽屉基础件，并保持浏览器现有入口与行为
4. [DONE] 实现环境变量搜索、按工具包类型分类、分组折叠、状态摘要和草稿编辑
5. [DONE] 删除旧弹窗专用实现，同步资源、语义文档和自动检查
6. [LOCAL DONE] 运行定向测试、正式开发门禁、差异检查和 Debug APK 构建核验
7. [PENDING] 在目标设备验收抽屉拖动、输入法、搜索、折叠与保存结果
8. [DONE] 为 17 个现有类型建立独立图标与浅深主题配色
9. [DONE] 压缩环境变量工具包头、变量项和横向分类条的垂直密度
10. [DONE] 类型按英文 A 到 Z、类型内按英文或中文拼音首字母排序
11. [DONE] 更新策略测试、语义文档并重新构建核验 Debug APK
12. [DONE] 每次打开时仅自动展开所有存在未填写必填变量的工具包，其余默认收起

设计与验收入口：

- [`package_environment_variables_drawer/`](package_environment_variables_drawer/index.md)

本地验收证据详见
[`package_environment_variables_drawer/`](package_environment_variables_drawer/index.md)；
本轮最终 APK 指纹为 `263252033D05E2E5FBCAECF882CAE1688F2032D329D1248070472CF7AE439776`。

## 2026-08-09 Python 环境与任务日记恢复运行时

状态：本地实现、Skill 全量验证、正式开发准备检查与 Debug APK 核验均已完成。

本轮只调整开发工作流，不改变 Android 产品行为或 Python 项目依赖。Kiyori `.venv` 继续服务
仓库自有 `ci/script` 与测试入口；Codex 全局 Skill 和控制面使用自身记录或指定的绝对运行时，
不向项目 `.venv` 安装与 Kiyori 无关的 `tzdata`。

细化步骤：

1. [DONE] 确认 IANA 失败来自 task-diary 错用项目 `.venv`，不是 Kiyori 运行依赖缺失
2. [DONE] 收窄全局和项目 `AGENTS.md` 的 Python 运行时路由边界
3. [DONE] 让 task-diary 新日记持久化 Python 与恢复命令，旧日记首次成功写入时透明补录
4. [DONE] 让 `resolve/list` 和 IANA 错误路径公开恢复命令来源，不伪称旧日记存在原始运行时
5. [DONE] 运行 task-diary 全量测试、Skill 快速验证、真实隔离恢复演练和数据边界审计
6. [DONE] 运行正式开发准备检查、差异检查和规定的 Debug APK 构建核验

本地验收证据：

- task-diary 全量测试 `65` 项通过；Skill `quick_validate.py`、Python 语法编译与目标差异检查通过
- 私有日记索引共 `187` 份，校验错误 `0`、索引拒绝 `0`；第二次增量索引读取正文 `0`
- 使用 Kiyori `.venv` 复现 IANA 时区失败后，错误信息明确给出已记录的系统 Python 和精确恢复命令；
  使用该命令成功恢复当前日记，未安装 `tzdata`，也未使用固定时差或其他回退逻辑
- `python -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功完成 `238` 个任务，
  其中 `25` 个执行、`213` 个为最新状态
- `D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk` 大小
  `475308770` 字节，SHA-256
  `2BEEE13F9CB206F8AC2EE9D5AA0DC85C0C2BA7B39B391863F374B2B20E903B0F`；
  `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`，
  Android Debug v2 签名与 `zipalign -c -P 16 4` 均通过

本轮未改变 Kiyori Python 依赖、未安装 `tzdata`、未安装 APK、未执行设备操作，也未提交或推送。

## 2026-08-09 Operit v1.12.1 必要 AI 插件发布更新

本轮只处理 Kiyori 上次 `1.12.0+9` 审计终点之后的真实增量。上游
`v1.12.1@4faa5cd2` 相对 `93a28251` 只新增市场登记失败后的 GitHub Release 资产保留修复，
同时包含不属于 Kiyori 的 Operit 应用版本号变更。

细化步骤：

1. [DONE] 核验 `v1.12.1` 标签、提交图、发布补丁和 Kiyori `1.12.0+9` 既有能力
2. [DONE] 确认角色卡选中发送等 AI 更新已落地，当前唯一缺口是登记失败后的破坏性资产清理
3. [DONE] 保留已上传 Release/asset，继续返回原有 `RegistrationFailed`
4. [DONE] 增加源码合同测试，保持同名资产上传前替换语义不变
5. [DONE] 执行正式开发门禁、差异检查、Debug APK 构建与产物核验
6. [DONE] 修复完整 Python 门禁中遗留的播放器长按倍速源码合同漂移
7. [DONE] 运行完整 Python、JVM、Lint、正式准备检查和最终 Debug 构建
8. [DONE] 审计敏感内容、构建产物、子模块、Git mode、异常大文件和暂存树
9. [DONE] 提交并推送 `main`，核对本地、跟踪和远端 ref 一致

Kiyori `versionCode/versionName` 和 `OPERIT_MARKET_COMPAT_VERSION=1.12.0+9` 均保持不变。
现有 WASM、A2A、远程 MCP、用户资料、主题、签名、国际化和发布素材不属于本轮必要范围。
本地自动验证与 Debug APK 核验已完成；未创建真实 GitHub Release 或触发线上登记失败，
远端发布链保持 `verification_pending`。
完整记录见
[`operit_1_12_1_ai_update/`](operit_1_12_1_ai_update/index.md)。

## 2026-08-09 网页浏览器返回、缩放、文字与网站密码设置

状态：本地实现与自动验证完成，目标设备网页行为保持 `verification_pending`。继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器产品能力的唯一进度载体，不创建第二套 Browser Runtime、设置 owner 或凭据数据库。
参考 `D:\10_Project\hikerView` 的 WebView 行为和指定截图的信息层级，但界面统一使用当前
`KiyoriSettingsUi` 折叠标题、分组卡片、Material Switch 与页面内子页。

细化步骤：

1. [DONE] 核对当前 `WebSessionBrowserSettingsStore`、网页 Back 状态机、WebView 设置应用点、
   参考源码与四张参考图片
2. [DONE] 将浏览器设置规划为“网页插件与脚本 / 主页与导航 / 网页显示 /
   网站权限与数据 / 音视频嗅探”五组
3. [DONE] 新增“返回不重载”，只改变现有网页历史 Back 的缓存策略；不改变浏览器顶栏返回、
   每窗口主页根、Shell 返回目标或 WebSession 生命周期
4. [DONE] 新增“强制页面缩放”，通过当前页面 viewport 合同解除网页禁止缩放限制；
   新旧页面和设置即时切换继续使用同一 WebView
5. [DONE] 新增“网页文字大小”子页，提供实时文字示例、`50%..200%` 的 `5%` 步进、
   增减与恢复默认操作，并由 `WebSettings.textZoom` 应用到全部现有及后续 WebSession
6. [DONE] 新增“网站密码管理”子页和唯一私有凭据 vault；自动保存默认关闭，只允许普通
   Profile 捕获和填充，按精确 HTTP/HTTPS origin 匹配，支持搜索、查看、复制、编辑和删除
7. [DONE] 凭据使用 Android Keystore AES-GCM 加密，文件位于 `noBackupFilesDir`；
   不记录日志、不进入当前原始快照备份、不向无痕 Profile 或其他网站暴露
8. [DONE] 增加设置结构、持久化约束、Back 缓存策略、缩放脚本、凭据 origin/脚本合同和
   管理行为测试，同步 `CONTEXT.md`、README 与浏览器设置里程碑
9. [LOCAL DONE] 运行定向 JVM、正式开发门禁、资源与差异检查，串行构建并核验 Debug APK；
   目标设备上的网页 Back、双指缩放、站点字体和真实登录表单保持单独验收

本地验收证据：

- 四组策略与设置页面定向 JVM：`22` 项通过，零失败、零错误、零跳过
- Lint 新问题与可见报告均为 `0`；基线保留 `5567` 项，`stale=0`、`current-only=0`
- Lint 清理同步修正五个非英语语言目录中的 `77` 个缺失 key（共 `385` 条翻译）、Compose
  资源读取、KTX、状态类型、Modifier 顺序和复数候选问题，并将 Media3 升级到 `1.11.0`
- 本轮相关 CI Python 测试 `115` 项通过；正式开发准备门禁、AAPT 资源处理和
  `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 于
  `2026-08-09 13:19:22 +08:00` 成功生成
  `D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk`
- APK 大小 `475308770` 字节，SHA-256
  `19DCD1D1F184344E6C196D896D30B70F454D0CAF1567BA8E2AF8ACEF3B96E2AB`；
  `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、
  `targetSdk 34`、`arm64-v8a`，v2 调试签名和 `zipalign -c -P 16 4` 均通过
- 本轮未安装 APK、未执行 ADB/MuMu/真机操作；真实网页连续 Back、动态 viewport、
  双指缩放、站点字体和登录表单保存/自动填充仍待目标设备验收

当前非目标：

- 不改变浏览器顶栏左侧返回 App Shell 入口页的语义
- 不给无痕窗口保存、读取或自动填充网站密码
- 不接入 Android 系统密码管理器、云同步、备份导入或跨设备凭据迁移
- 不增加第二个 WebView、第二套历史、第二份设置状态或任何回退路径
- 不提交、不推送、不安装 APK、不执行 ADB、MuMu 或真机操作

## 2026-08-08 AI 助手模型名称标签管理

状态：第五轮三行折叠、真实隐藏数量标签与清空反馈修复已完成本地实现、定向验证和 Debug APK 核验；Android 目标设备上的视觉与触摸验收仍待执行。继续使用稳定的自定义测量和纯行计划，不恢复高度裁切或已否决的 Flow overflow API。继续保留 `ModelConfigData.modelName` 作为既有序列化边界，不创建第二个模型数据源。

设计与验收入口：

- [`ai_model_tag_management/`](ai_model_tag_management/index.md)

本轮范围：

- 手动输入一个或多个模型名称，按逗号或换行解析后去重追加
- 从当前 API 上游获取模型列表，支持搜索、多选和追加
- 标签点击复制、叉号删除、长按进入纵向排序面板
- 收起时只放置三行完整标签和无叉号的“还有 N 个”，`N` 为真实隐藏数量；展开时显示全部
- 第一项明确显示为测试模型，连接测试结果展示实际测试模型名
- 排序时按模型名修正功能模型与固定角色卡绑定的索引
- 删除已绑定模型时明确提示影响范围并指定新的第一项；唯一且仍被绑定的模型不可直接删除
- 使用现有模型获取、配置保存、连接测试和 `EnhancedAIService` 刷新链路
- 将“添加模型 / 从上游获取 / 更多”统一为同一行、同高度和同圆角的操作栏
- 将“重命名 / 删除 / 测试模型”统一为同一行，并让测试入口保持更明确的主操作层级
- 将上游模型长列表改为设置页风格的底部面板，统一搜索、刷新、全选、列表和底部确认区
- 移除导致搜索提示文字裁切的 `48dp` 强制高度，按 Material3 输入框最小高度布局
- 连接测试仍以第一项模型为测试目标，并覆盖聊天以及已启用的 Tool Call、识图、音频和视频能力
- 上游面板允许取消勾选当前已有模型，应用时只同步当前上游范围内的添加和移除差异
- 上游未返回的手动或历史模型保持原顺序；移除绑定模型继续经过明确确认
- 取消标签固定最大宽度并增加内部留白；撤销高度裁切，使用三行放置计划和完整的“还有 N 个”标签
- 六个模型操作按钮统一为共享的 `40dp` 高度与 `12dp` 圆角
- 清空全部立即显示检查状态；无绑定时确认清空，有绑定时显示明确阻止对话框

本轮非目标：

- 不改写 API 提供商协议、模型获取协议、备份格式或外部 WebChat 的模型索引协议
- 不新建独立模型数据库、第二个模型列表持久化字段或并行状态 owner
- 不改变既有错误处理策略，不引入第二条模型获取或保存路径
- 不提交、不推送、不安装 APK、不执行 ADB、MuMu 或真机操作

本地证据：

- 首轮 4 个相关测试类共 `26` 项测试通过；第二轮 `ModelNameListTest` 与 `ModelNameTagEditorContractTest` 共 `10` 项测试再次通过，失败、错误和跳过均为 `0`
- 正式开发准备门禁通过；`git diff --check` 无 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 构建成功，238 个任务中 29 个实际执行，并通过单 Launcher 与播放器运行时打包校验
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`com.kiyori`，`0.1.0 (45)`，`471616082` bytes，`arm64-v8a`
- APK SHA-256：`EFB9C79C96B59ED3ABB574E2D868F93FE4F68D3693DF75806562EDF6599CF261`
- Android Debug V2 单签名验证与 `zipalign -c -P 16 -v 4` 验证通过
- 第三轮 `ModelNameListTest` 与 `ModelNameTagEditorContractTest` 共 `12/12` 通过；正式门禁、Markdown 解析器和差异检查通过
- 第三轮 APK：`471619002` bytes，SHA-256 `B343051729C55FCAE71F48A196DA1412788AFACDF4466E3C81BAEC7EC1DBC9E3`，Debug V2 单签名与 16KB zipalign 通过
- 第四轮首次实现改用普通 `FlowRow` 两行上限并删除旧裁切，但其组合阶段读取 `shownItemCount` 会触发 APP_FATAL
- 第四轮定向测试 `12/12`、formal readiness、Markdown 检查器 `7/7`、8 份文档链接扫描和 `git diff --check` 通过
- 第四轮首次 APK（已否决）：`471619002` bytes，SHA-256 `4EB315694445FFD53781DCBD3000FB230946C84D5CAE6D2743E3F22D6CA99B3F`
- APP_FATAL `b79f91c3-11c9-4ea5-87d0-00e34a812f79` 指向 `ModelNameTagEditor.kt:194`；修正版删除全部 Flow overflow API，使用 `Layout` 与 `planModelTagFlow`
- 修正版定向测试 `18/18`、formal readiness、Markdown 检查器 `7/7`、8 份文档链接扫描和 `git diff --check` 通过
- 修正版 APK：`471618990` bytes，SHA-256 `A34675DE5E4745ABE6BD45C9C5C60EB547865CBC5A3D1566EDC626AB8A09554A`，Debug V2 单签名与 16 KB zipalign 通过
- 第五轮将折叠上限改为三行，使用 `SubcomposeLayout` 先测量完整标签和最大计数展开标签，再由纯行计划返回真实隐藏数量；“还有 N 个”标签没有删除叉号
- 第五轮清空全部在启动异步绑定检查前立即显示 `Checking` 模态状态，随后进入 `Confirm` 或 `Blocked`，不再把关键阻止反馈放在长页面末尾
- 第五轮 `ModelNameListTest`、`ModelTagFlowPlanTest` 与 `ModelNameTagEditorContractTest` 合计 `19/19` 通过，失败、错误和跳过均为 `0`
- 第五轮 formal readiness、Markdown 检查器 `7/7`、8 份文档链接扫描和 `git diff --check` 通过
- 第五轮 Debug APK：`471620006` bytes，SHA-256 `4642C2CD74B5F7A82729B44D4F004D879B2737807D5D058BD9DC948E7FE5A857`，Debug V2 单签名与 16 KB zipalign 通过
- 第五轮构建主机文件时间为 `2026-08-09 02:57:45 +08:00`，仅作为产物元数据；当前会话日期仍为 `2026-08-08`

## 2026-08-08 Operit `1.12.0+8/+9` AI 与 ToolPkg 兼容更新

状态：上游版本、实时市场和 Kiyori 公共脚本接口差异审计、必要实现、本地自动验证和
Debug APK 制品核验已完成；目标设备交互保持待验证。
本轮严格参考 Operit `3f146057`、`4bf0138b` 及 `+9` 后同日修复，不整分支合并，不用
Kiyori 产品版本冒充 Operit 生态兼容版本。

实施与验收步骤：

1. [DONE] 核实 `+8/+9` 版本提交、共同祖先、上游后续修复和实时市场最新包
2. [DONE] 下载并校验全部 19 个 `minAppVer=1.12.0+9` 最新 ToolPkg，提取实际 Hook 与
   `Tools.*` API 需求
3. [DONE] 补齐 ChatMessage Hook、Hook 总时限与聊天可见超时提示、角色卡
   `SoftwareSettings`、定位地址参数和市场兼容判断
4. [DONE] 移植待发送队列、超大消息安全读取、选中角色卡发送、默认 Tool Call、
   DeepSeek low、超长粘贴附件化和记忆提取规则
5. [DONE] 同步内置 ToolPkg、TypeScript 声明、插件开发文档、`CONTEXT.md`、README
   与正式开发准备兼容基线
6. [DONE] 增加或移植定向测试，执行正式开发门禁、差异检查和必要的源码/资源检查
7. [DONE] 串行构建并核验 Debug APK；真机市场安装、聊天 Hook、超时提示和插件 UI
   继续单独记录

当前非目标：

- 不移植 A2A 外部服务、发布签名、终端会话恢复、上游目录搬迁、主题系统或 CI 跳过策略
- 不建立第二套 ToolPkg、市场、聊天、角色卡、数据库或记忆状态 owner
- 不增加回退、降级或兼容旁路；不提交、不推送、不安装或操作设备

本地证据：

- `message_insert` TypeScript 编译通过；ToolPkg、市场兼容、队列隔离和粘贴识别 4 个
  JVM suite 共 `14/14` 项通过，零失败、零错误、零跳过
- formal readiness 通过；Markdown 检查器单测 `7/7`；`git diff --check` 无错误
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 31s`，238 个任务中 34 executed / 204 up-to-date；
  唯一 Launcher 与播放器运行时打包校验通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 22:55:52 +08:00`，大小 `471598782` 字节，SHA-256
  `D0B162DAD2AC9669F09292DCC108C9C858C74CFEC30DF9EE3711365A4C48FFE4`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名且
  `zipalign -c -P 16 -v 4` 通过
- APK 内嵌 `message_insert.toolpkg` 与 Gradle 生成物逐字节一致，大小 `18320` 字节，
  SHA-256 `A1EB5B57BBC5168D75A3E6FCBD2906EC1A64B1D0576F0274B0D34D9FFA4CED28`

详细计划见
[`operit_1_12_0_plus_8_plus_9_ai_plugin_update/`](operit_1_12_0_plus_8_plus_9_ai_plugin_update/index.md)。

## 2026-08-08 浏览器插件与负一屏入口配色调整

状态：本地实现、自动验证和 Debug APK 已完成；目标设备视觉验收待完成。Kiyori 当前未发布，本轮直接迭代现有浏览器 18 色身份表和负一屏颜色映射，
不保留旧洋红插件色兼容开关，不增加第二套主题或状态 owner。

本轮细化步骤：

1. [DONE] 审计插件入口、插件中心“本页 / 已安装 / 更新 / 日志”、插件卡主图标、负一屏四张
   数据卡和八个快捷工具的颜色调用链
2. [DONE] 将 `PLUGINS` 从高饱和洋红调整为低饱和深梅紫，并让插件中心各子页的
   插件主图标统一复用该入口色；错误、成功、更新风险等状态提示继续使用状态语义色
3. [DONE] 让负一屏“收藏 / 书签 / 历史 / 下载”分别复用浏览器菜单
   `ADD_BOOKMARK / BOOKMARKS / HISTORY / DOWNLOADS` 的身份色
4. [DONE] 为“新版 / 手册 / 版本 / 搜索 / 工具箱 / 清理 / 备份 / 退出”选择八个
   两两不同且语义接近的现有浏览器入口色
5. [DONE] 增加插件色、负一屏四入口一致性和八色唯一性测试，同步 `README.md`、
   `CONTEXT.md` 与浏览器产品完成清单
6. [DONE] 运行定向 JVM、正式开发门禁、Markdown/架构/差异检查和最终 Debug APK
   构建与制品核验
7. [PENDING] 目标设备验收插件深梅紫、各插件子页、负一屏四入口及八个快捷工具在浅色/
   深色主题下的实际观感与可读性

当前非目标：

- 不改变插件、书签、历史、下载、Browser Runtime、用户脚本或负一屏快捷工具的功能和状态所有权；
  “收藏”继续保持零计数和空点击，专用于未来小程序服务
- 不用插件身份色覆盖错误、危险、执行成功、更新安全性等局部状态反馈
- 不安装 APK、不操作 ADB、MuMu 或真机；不创建提交、不推送远端

本地证据：

- 9 个相关 JVM suite 共 `89/89` 项通过，零失败、零错误、零跳过；其中颜色策略 `5/5`、
  负一屏与设置页 `12/12`、Shell 返回/恢复 `53/53`、顶栏返回策略 `2/2`
- ARCH041 消费者架构正反向测试 `2/2` 通过；完整 `phase=m03` 架构门禁只剩任务开始前已记录且
  本轮未修改的 ARCH046 `UserscriptSourceExportHelper.kt` 消费者快照漂移
- formal readiness 通过；Markdown 单测 `7/7`，257 个工作树 Markdown 文件缺失本地链接为
  `0`；`git diff --check` 和新增 Kotlin 禁用兜底语义扫描通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 52s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 20:08:41 +08:00`，大小 `471581414` 字节，SHA-256
  `B585A865CE7C349EB41897F1B568E78B8802F8FF13DC95F22DB23C3DCEDC9084`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名、
  `zipalign -c -P 16 -v 4` 通过

## 2026-08-08 浏览器顶栏返回入口页

状态：本地实现、自动验证和 Debug APK 已完成；目标设备上的入口页返回、浏览器状态恢复和
网页前进/后退交互待验收。Kiyori 当前未发布，本轮直接修正浏览器顶栏返回语义，不保留旧的
逐级网页回退接线，不增加第二套 Browser Runtime、WebView、窗口或 Shell 导航状态。

本轮细化步骤：

1. [DONE] 将浏览器顶栏左侧返回与系统 Back、底栏网页后退解耦；顶栏直接结束
   Browser Home 展示，系统 Back 和底栏左右按钮继续处理临时界面及网页历史
2. [DONE] 扩展 Shell 的浏览器返回目标，使软件首页三页、AI 对话、微应用、文件管理和设置首页
   进入浏览器后都能返回原入口页
3. [DONE] 复用现有 presentation release 与 `KiyoriShellState.exitBrowser()`，保留唯一
   WebSession、活动 WebView、窗口顺序、网页历史和 Profile 状态
4. [DONE] 增加顶栏 release mode 与各入口页返回目标测试，同步 `README.md`、`CONTEXT.md`
   和浏览器产品完成清单
5. [DONE] 运行定向 JVM、正式开发门禁、Markdown/差异检查和 Debug APK 构建与制品核验
6. [PENDING] 目标设备验收从 AI 对话、设置首页及其他底栏入口进入后的顶栏返回、浏览器状态恢复、
   系统 Back 和底栏网页前进/后退

当前非目标：

- 不改变底栏网页后退/前进、主页按钮、窗口管理、网页历史根或 AI `browser_navigate_back`
- 不改变浏览器菜单“退出浏览器”和最小化 indicator 的既有语义
- 不安装 APK、不操作 ADB、MuMu 或真机；不创建提交、不推送远端

本地证据：

- 顶栏策略、Shell 返回目标和原浏览器 Back 策略 3 个定向 JVM suite 共 `56/56` 项通过，
  零失败、零错误、零跳过
- formal readiness 通过；Markdown 检查器单测 `7/7`，257 个工作树 Markdown 文件缺失本地链接为
  `0`；`git diff --check` 和新增代码禁用兜底语义扫描通过
- 完整 `phase=m03` 架构门禁执行 37 段；本轮触及的 ARCH020、ARCH021、ARCH023、ARCH024
  均通过，只报告任务开始前已记录且本轮未修改的 ARCH046
  `UserscriptSourceExportHelper.kt` 消费者快照漂移
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 22s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 19:35:38 +08:00`，大小 `471581414` 字节，SHA-256
  `B274A738017A4F371A42F4003A503F7137A3964BDC4510334EF48095C802F7E3`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名且
  `zipalign -c -P 16 -v 4` 通过；包含 `liboperit_ripgrep.so` 与
  `assets/operit_shell_exec`，不包含 `libsudo.so`

## 2026-08-08 浏览器四行菜单十八色统一

状态：本地实现、自动验证和 Debug APK 已完成；目标设备上的亮暗主题视觉与触控验收待完成。
Kiyori 当前未发布，本轮直接完善现有浏览器菜单视觉，不保留旧配色兼容开关，不增加回退路径，
也不改变按钮顺序、路由、Browser Runtime 或各功能状态 owner。

本轮细化步骤：

1. [DONE] 审计四行菜单 18 个按钮、共享抽屉标题、弹窗、搜索/筛选强调色、空态和搜索框右侧
   资源数字球的颜色调用链
2. [DONE] 建立浏览器菜单专属 18 色稳定映射，浅色与深色主题下每个入口的图标色和
   低饱和容器色均保持两两不同
3. [DONE] 将书签、历史、下载、插件、悬浮嗅探、UA、网络日志、工具箱、阅读模式、
   查看源码、广告标记和网站配置的子页面身份 UI 统一为对应入口色
4. [DONE] 让搜索框右侧资源数字球直接复用“悬浮嗅探”入口颜色
5. [DONE] 增加颜色唯一性、菜单顺序及入口/子页面/数字球一致性测试，并同步浏览器产品文档
6. [DONE] 运行定向 JVM 测试、正式开发门禁、Markdown/差异检查和 Debug APK 构建与制品核验
7. [PENDING] 目标设备验收四行菜单、各子抽屉/弹窗、亮暗主题和资源数字球的实际颜色与对比度

当前非目标：

- 不改变普通/无痕窗口蓝紫身份色、按钮功能、页面导航、下载/媒体/脚本数据或持久化格式
- 不用入口色替换删除危险、执行成功、媒体分类、网络分类等局部状态语义色
- 不安装 APK、不操作 ADB/MuMu/真机；不创建提交、不推送远端

本地证据：

- `WebSessionBrowserMenuColorPolicyTest` 固定 18 个入口、`5/5/5/3` 四行结构、浅色与深色图标/
  容器/组合两两唯一、全部不低于 `3:1` 非文本对比度，以及资源数字球对
  `FLOATING_SNIFFER` 的直接复用
- 颜色、浏览器视觉、下载、媒体候选、网络日志、页面源码、UA、书签、历史和用户脚本共
  11 个定向 JVM suite、`70/70` 项通过，零失败、零错误、零跳过
- ARCH024 App Shell 与 ARCH041 语义色消费者的 4 个正负契约测试通过；完整 `phase=m03`
  架构检查只剩本轮开始前已记录的 ARCH046
  `UserscriptSourceExportHelper.kt` 消费者快照漂移，本轮未修改其存储 owner
- formal readiness 通过；Markdown 检查器 `7/7`、`git diff --check` 和新文件禁用回退语义
  扫描通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 3s`，238 个任务中 29 executed / 209 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 17:34:23 +08:00`，大小 `471581414` 字节，SHA-256
  `F7DCF31472EC162B7BE4ADAA18C02E76D6DB4D89C8C55F5036E99E1D99ACEE54`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名且
  `zipalign -c -P 16 -v 4` 通过；包含 `liboperit_ripgrep.so` 与
  `assets/operit_shell_exec`，不包含 `libsudo.so`

## 2026-08-08 首页底栏与浏览器彩色弹窗统一

状态：上一批与用户复核增量均已完成本地实现、自动验证和 Debug APK，目标设备视觉与交互验收待完成。
Kiyori 当前未发布，本轮直接完善现有界面，不保留旧视觉兼容开关，不增加回退路径，也不改变
Browser Runtime、下载、UA、媒体候选或页面源码的状态 owner。

本轮细化步骤：

1. [DONE] 让负一屏或 AI 首页向软件首页拖动时，底部五入口按 Pager 实时偏移立即显现；静止在
   负一屏、AI 首页、AI 深层页面或打开 Shell 子层/抽屉时仍保持隐藏
2. [DONE] 为浏览器窗口总览建立普通蓝色、无痕紫色语义色；顶部保持同一行文字标签和不同颜色
   下划线，不绘制按钮容器；空态、新建按钮、活动卡片、缩略图占位和身份标记继续使用对应色
3. [DONE] 增加共享语义弹窗 Surface；“我的下载”使用绿色抽屉标题与操作弹窗，UA 选择/编辑使用
   蓝色弹窗，媒体候选长按及链接查看使用青色弹窗
4. [DONE] 为页面源码超长行信息条增加右侧关闭按钮；关闭状态只绑定当前 session 与
   Document token，不改变源码、软换行或统计数据
5. [DONE] 同步 `README.md`、`CONTEXT.md` 和浏览器产品能力分项文档
6. [DONE] 运行 Shell/Profile/源码定向 JVM 测试、formal readiness、Markdown/差异检查和
   禁用回退语义反向扫描
7. [DONE] 串行执行 `:app:assembleDebug --no-daemon --console=plain`，核验 Debug APK 的时间、
   大小、SHA-256、包名、版本、唯一 launcher、V2 签名与 16 KB ZIP 对齐
8. [PENDING] 目标设备验收拖动中底栏首帧、普通/无痕亮暗主题、窄屏下载标题动作区、UA 输入法、
   嗅探资源长按弹窗和源码提示关闭/重新抓取

当前非目标：

- 不修改普通/无痕 Profile 隔离、标签创建/清理、下载队列、UA 优先级、媒体候选排序或源码应用语义
- 不创建第二套弹窗状态、下载 owner、Browser Runtime 或持久化字段
- 不安装 APK、不操作 ADB/MuMu/真机；不创建提交、不推送远端

本地证据：

- JDK 21 下 `:app:compileDebugKotlin` 通过；7 个定向 JVM suite 共 `99/99` 项通过，零失败、
  零错误、零跳过，其中 `KiyoriShellStateTest 51`、窗口视觉策略 `1`、下载抽屉策略 `10`、
  UA 策略/路由 `7`、媒体候选策略 `19`、页面源码支持 `11`
- formal readiness 通过；Markdown 检查器单测 `7/7`，8 个本轮修改 Markdown 文件新增缺失本地链接
  为 `0`；`git diff --check` 和新增源码禁用回退语义扫描通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 57s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 15:23:06 +08:00`，大小 `471581414` 字节，SHA-256
  `1922760B24152AF916BECDEC5846CC1017FD7A13B53F79E228B62B0525E2EFA6`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名与
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK，未操作 ADB、MuMu 或真机；拖动首帧、亮暗主题和窄屏弹窗继续保持
  `verification_pending`

### 用户复核增量

1. [DONE] 普通窗口和无痕窗口保持等宽、同一行的文字标签，标题强制单行；普通使用蓝色下划线，
   无痕使用紫色下划线，选中项只改变文字强调、线条透明度和粗细
2. [DONE] 配置自定义首页时，清空当前 Profile 后复用现有 `onNewTab(profile)` 创建主页窗口并
   关闭窗口总览；默认 `about:blank` 继续显示剩余 Profile 或空总览
3. [DONE] `WebSessionDrawerHeader` 增加标题后动作槽位；下载四竖线保留 `36dp` 点击热区，视觉从
   标题后 `4dp` 开始绘制，新增/清理动作仍位于标题栏右侧
4. [DONE] JDK 21 下 Debug Kotlin 编译通过；窗口、主页、Profile 与下载抽屉 5 个 suite 共
   `29/29` 通过，零失败、零错误、零跳过
5. [DONE] 重新运行 formal readiness、Markdown/差异检查和最终 Debug APK 构建与制品核验
6. [PENDING] 目标设备验收 320dp 窄屏单行标签、蓝/紫横线、清空后的自定义首页和下载菜单位置

用户复核增量证据：

- JDK 21 下 `:app:compileDebugKotlin` 为 `BUILD SUCCESSFUL in 1m 26s`；窗口清空、主页导航、
  Profile 与下载抽屉 5 个定向 suite 共 `29/29` 通过，零失败、零错误、零跳过
- formal readiness 通过；Markdown 检查器单测 `7/7`，8 个修改 Markdown 文件缺失本地链接为 `0`；
  `git diff --check` 无 whitespace error，新增代码禁用回退语义扫描零命中
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 55s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 15:47:40 +08:00`，大小 `471581414` 字节，SHA-256
  `8E1C37CBD651B36417DDF075741802E58FD086AD82CBA884C600F1FE866785C3`
- APK 为 `com.kiyori / 45 / 0.1.0 / compileSdk 37 / minSdk 26 / targetSdk 34 / arm64-v8a`；
  唯一 launcher、Android Debug V2 单签名与 `zipalign -c -P 16 -v 4` 通过

### 用户复核增量：抽屉标题栏与弹窗统一

1. [DONE] 下载四竖线继续位于“载”字右侧并保留 `36dp` 热区，按压反馈通过
   `CircleShape` 裁剪为圆形，图形在圆形反馈内水平和垂直严格居中，不再出现正方形点击阴影
2. [DONE] 书签、历史、下载、网络日志、视频资源、占位功能页、插件和用户脚本子页全部复用
   `WebSessionDrawerHeader`；顶部统一为 `52dp` 高度、`34dp` 语义徽标、`8dp` 图标标题间距和
   同一标题字级，网络日志标题不再紧贴图标
3. [DONE] 网络日志操作/详情、书签编辑/文件夹选择/确认、下载排序/新增/完整链接/删除/长按操作/
   重命名、用户脚本安装/删除/日志详情/元数据/审阅等项目自定义弹窗全部复用
   `WebSessionBrowserDialogSurface`；统一语义色徽标、`20dp` 圆角、细描边、标题分隔线、阴影和
   全窗口遮罩。历史删除时间范围保留底部选择交互，但复用同一弹窗标题头
4. [DONE] 增加浏览器视觉策略测试，固定共享标题栏几何、标题动作圆形反馈及内容严格居中合同；
   最终居中修正后的浏览器视觉与下载抽屉 2 个 suite 共 `13/13` 通过
5. [DONE] JDK 21 下 Debug Kotlin 编译通过；下载、网络日志、媒体、书签、历史、用户脚本和浏览器
   视觉策略 7 个 suite 共 `48/48` 通过，零失败、零错误、零跳过
6. [DONE] formal readiness、Markdown 检查器 `7/7`、8 个修改 Markdown 文件本地链接扫描和
   `git diff --check` 通过
7. [DONE] 串行构建 Debug APK；最终居中修正版 `:app:assembleDebug` 为
   `BUILD SUCCESSFUL in 53s`，238 个任务中 28 executed / 210 up-to-date，唯一 Launcher 与
   播放器运行时打包校验通过
8. [PENDING] 目标设备验收 320dp 窄屏顶部动作、网络日志标题间距、抽屉拖动、亮暗主题及各类
   弹窗的输入法、滚动和系统 Back

本轮制品：

- `app/build/outputs/apk/debug/app-debug.apk`
- 生成时间 `2026-08-08 16:36:26 +08:00`，大小 `471581414` 字节，SHA-256
  `089BBDE3288D410E5B3C731EE9E2A5E0087B6ED0276E72AF959189198F7D5408`
- `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`
- 唯一 Launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单签名，
  `zipalign -c -P 16 -v 4` 通过
- 未安装 APK、未操作 ADB、MuMu 或真机；未创建提交、未推送远端

## 2026-08-03 浏览器插件与脚本模块初步封板

状态：本地实现、自动验证与 Debug APK 完成，目标设备验收待完成。继续复用唯一
`StandardBrowserSessionTools`、`BrowserPluginCenterFacade`、
`UserscriptRepository` 和 `WebSessionUserscriptManager`，不创建第二 Browser Runtime、插件注册表、
脚本仓库、日志 owner 或设置状态源。

本轮只读审查确认：

- 网页浏览器设置当前共显示 `30` 行，其中 `17` 行没有真实 action、owner 或 consumer，只能以禁用占位
  展示；这些选项不应继续占用正式设置界面
- “网页插件与脚本”组的插件中心、油猴脚本管理、权限、当前页诊断和日志入口存在重复导航；
  权限子页又重复提供插件中心、管理、诊断和日志入口
- 油猴工作台在当前 Android System WebView 不支持 userscript runtime 时，仍可从右上角菜单和
  授权横幅请求开启用户脚本
- 插件中心“本页”只渲染 provider 级卡片，但标签数量使用 userscript 条目数，计数语义不一致
- 批量删除 userscript 时，任一删除异常会中断剩余项目且没有明确失败反馈
- userscript 值、草稿和日志按数值 `scriptId` 绑定；删除 registry 后重新安装会获得新 ID，
  因此直接增加“保留数据”开关只会制造无法恢复的孤儿数据

实施与验收顺序：

1. [DONE] 浏览器设置只保留连接真实 owner 的选项，删除全部无 action 的禁用占位；将页面收敛为
   “网页插件与脚本 / 主页与网站数据 / 音视频嗅探”三组
2. [DONE] “网页插件与脚本”收敛为总授权、插件中心、权限与网站范围、诊断与日志四个入口；
   权限子页只保留运行环境和逐脚本权限，不重复导航
3. [DONE] 修复 runtime 不支持时仍可请求授权的问题；插件中心“本页”标签按实际 provider 卡片计数
4. [DONE] 增加已安装 userscript 源码导出，输出 `.user.js` 到 `Download/Kiyori/exports`；
   删除仍明确清理源码、修订、草稿、值和日志，不提供无恢复能力的数据保留伪选项
5. [DONE] 让单项与批量删除逐项处理错误、记录日志并向用户反馈，不因一个失败静默中断后续删除
6. [DONE] 更新设置、插件投影、userscript UI 与删除链路测试；同步 `README.md`、`CONTEXT.md`、
   浏览器插件架构和实施 TODO
7. [DONE] 运行 formal readiness、定向 JVM 测试、资源/XML 与 Markdown 检查、`git diff --check`，
   再串行构建并核验 Debug APK
8. [ ] 目标设备验收设置精简、深层路由、unsupported 状态、源码导出、删除失败反馈、抽屉计数和
   窄屏交互；本轮未获设备操作授权，不安装 APK

当前非目标：

- 不实现 `.kbx`、WebExtension、CRX/Edge 扩展直接安装、background worker 或新插件 provider
- 不扩大 userscript 注入、权限、GM API、匹配和网络能力
- 不把用户脚本、AI ToolPkg、Skill、MCP、工作流或包管理合并
- 不提交、不推送、不部署、不操作设备

本地证据：

- 聚焦 JVM 测试共 `31/31` 通过：`KiyoriSettingsPagesTest 11/11`、
  `KiyoriBrowserPluginSettingsPolicyTest 2/2`、`WebSessionUserscriptUiPolicyTest 2/2`、
  `UserscriptManagementPolicyTest 7/7`、`BrowserPluginCenterFacadeTest 9/9`
- formal readiness 通过；7 份 `strings.xml` 均可解析；7 个本轮修改 Markdown 文件没有新增失效本地链接；
  旧设置 action、旧设置 helper 和旧 `6/7/4/6/6` 当前合同均为零引用
- `git diff --check` 无 whitespace error，仅报告工作树中
  `WebSessionUserscriptManager.kt` 的既有 CRLF -> LF 提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 26s`，238 个任务中 29 executed / 209 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-04 01:04:30 +08:00`，大小 `466522199` 字节，SHA-256
  `51F3B5A558C80C4CEDA11063FBEB0E4358319D1C931476C05E9D37546A304C0F`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / minSdk 26 / targetSdk 34 /
  arm64-v8a`；Android Debug V2 签名与 `zipalign -c -P 16 -v 4` 均通过
- 未执行 APK 安装、ADB、MuMu 或真机测试；未创建提交、未推送远端

### 浏览器设置返回当前标签页插件抽屉

用户复测确认：从当前网页的浏览器菜单第四行第三个“设置”进入网页浏览器设置后，点击“插件中心”
或“脚本诊断与日志”，浏览器内部已经切换到目标插件路由，但设置子页仍覆盖在当前 Browser Home 上方，
因此无法看到当前标签页和浏览器下拉抽屉。

本轮修复计划：

1. [DONE] 追踪 Browser Home、`KiyoriShellChild.BROWSER_SETTINGS`、
   `BrowserPresentationCoordinator`、浏览器插件路由和 `ACTION_OPEN_KIYORI_BROWSER` 的完整链路
2. [DONE] 浏览器设置中的插件中心、userscript 工作台和脚本详情入口先关闭当前
   Browser Settings child，再委托唯一 `BrowserPresentationCoordinator` 打开对应浏览器插件路由
3. [DONE] 锁定关闭 Browser Settings child 会恢复 Browser Home，同时保留当前活动标签、
   `browserReturnTarget` 和 `browserExitPresentation`
4. [DONE] 同步浏览器设置和插件平台文档，执行 Shell/插件定向测试、formal readiness、
   Markdown 与差异检查
5. [DONE] 串行构建并核验新的 Debug APK；设备上的原网页、抽屉动画和系统 Back 复测继续保持
   `verification_pending`

修复不创建第二 Browser Runtime、第二标签状态或设置来源字段，不导航、刷新或重建当前网页，也不增加
没有明确所有权的回退路径。

当前验证证据：

- `KiyoriSettingsPagesTest 12/12`、`KiyoriShellStateTest 50/50`、
  `WebSessionBrowserBackPolicyTest 2/2`、`KiyoriBrowserPluginSettingsPolicyTest 2/2`，
  合计 `66/66` 通过，零失败、零错误、零跳过
- `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
  通过
- Markdown 检查器单元测试 `7/7` 通过；当前工作树 7 个修改 Markdown 文件的本地链接检查为
  `0` 个缺失目标
- `git diff --check` 无 whitespace error，仅报告工作树中
  `WebSessionUserscriptManager.kt` 的既有 CRLF -> LF 提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 55s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-04 01:32:17 +08:00`，大小 `466522199` 字节，SHA-256
  `7A42A35A16E6E1F8C95A95CF816A2CCD4D20581B4AA469E6D5767F91F6444A93`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名与
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK，未操作 ADB、MuMu 或真机；当前网页、抽屉动画、拖动和系统 Back 现场复测保持
  `verification_pending`

## 2026-08-03 播放器倍速、小窗与视频嗅探策略优化

状态：本地实现、自动验证与 Debug APK 完成，目标设备验收待完成。继续复用唯一
`PlayerSettingsStore`、`PlayerSession`、
`WebSessionBrowserSettingsStore`、每个 `WebSession` 的 `BrowserMediaCandidate` 和
`BrowserDownloadManager`，不创建第二播放器、第二候选列表或第二设置 owner。

本轮实施与验收顺序：

1. [DONE] 把播放器倍速固定选项扩展为 `0.25x..3.0x` 的 `0.25x` 步进，弹窗保持从高到低且压缩
   选项垂直间距；增加 `0.00x..3.00x`、最多两位小数的自定义倍速输入，其中 `0.00x` 明确执行暂停
2. [DONE] 把未持久化过的“双击手势”默认值改为“暂停/播放”；长按加速按当前倍速分段到
   `1.0x / 2.0x / 3.0x`，松手或取消后恢复按下前速度且不写入倍速记忆
3. [DONE] 让浏览器小窗继续按比例投影横屏播放器的顶部、侧边和底部控制结构，并与全屏播放器共用
   蓝紫进度色、轨道颜色、渐变遮罩和倍速选择控件
4. [DONE] 消除自动小窗对整个候选列表重复等待 `1.2s` 的路径：以最终候选 ID 为稳定键，只保留短暂
   稳定等待；候选 DOM 观察补充尺寸变化事件，不主动请求媒体或改写链接
5. [DONE] 从 DOM 尺寸及原始 URL 中识别 `2160P / 1440P / 1080P / 720P / 480P` 等画质线索；
   保持推荐资格优先，在推荐组内按画质从高到低排序，并让自动小窗选择达到门槛的最高画质候选
6. [DONE] 在浏览器“音视频嗅探”设置组增加“自动悬浮最小时长”：默认 `1 分钟`，提供
   `30 秒 / 1 / 3 / 5 / 10 / 30 / 60 分钟` 和 `1..86400 秒`自定义值；未知时长的非直播候选
   在获得可验证时长前不自动弹出小窗
7. [DONE] 同步 `CONTEXT.md`、`README.md`、播放器和嗅探阶段文档，补充纯策略与设置页面测试，
   再运行 formal readiness、`git diff --check`、相关 JVM 测试以及规定的 Debug APK 构建与制品核验
8. [ ] 在目标设备验收倍速弹窗输入法与滚动、双击/长按手势、小窗出现时延、画质排序、默认最高
   画质、阈值边界、悬浮/全屏往返和进度条视觉

本地证据：

- 聚焦 JVM 测试共 `59/59` 通过：`PlayerPolicyTest 24/24`、
  `BrowserMediaCandidatePolicyTest 19/19`、`KiyoriSettingsPagesTest 11/11`、
  `KiyoriBrowserPluginSettingsPolicyTest 2/2`、`PlayerControlsPolicyTest 3/3`
- `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
  通过；`git diff --check` 无 whitespace error，仅报告既有 CRLF -> LF 提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 25s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-03 23:59:51 +08:00`，大小 `465086902` 字节，SHA-256
  `1D7FB126D229E443E8362551189E2AD017DD827BCB325E394236C6528B75BB58`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / minSdk 26 / targetSdk 34`；
  Android Debug v2 签名与 `zipalign -c -P 16 -v 4` 均通过
- 未执行 APK 安装、ADB、MuMu 或真机测试；未创建提交、未推送远端

## 2026-08-03 浏览器主页确认与多窗口回退修复

状态：本地实现与自动验证完成，设备交互待验证。实现位于唯一
`StandardBrowserSessionTools` / `WebSession` Browser Runtime 内，没有创建第二套导航、
窗口或 WebView 状态。

本轮实施与验收顺序：

1. “网页主页自定义”中的“恢复为空白页”先显示确认弹窗；取消不写设置，确认后才把唯一
   `WebSessionBrowserSettingsStore.homeUrl` 设为 `about:blank`
2. 在每个现有 `WebSession` 内记录该窗口自己的主页根导航状态，覆盖直接从软件首页搜索、
   书签、用户脚本、AI 或网页弹窗创建且没有主页历史的窗口
3. 系统 Back、浏览器顶栏返回和底栏返回当时统一进入 `WebSessionBrowserHost` 的逐级状态机：
   先关闭临时界面，再后退当前窗口的有效网页历史；历史耗尽且尚未位于当前自定义主页时，
   导航到主页并建立新的历史根；只有已经位于主页根时才按入口关闭或最小化 Browser Home。
   其中顶栏接线已由 2026-08-08 的当前任务替代，现只保留系统 Back、底栏返回和 AI 浏览器后退
4. 切换或关闭窗口后只读取新活动 `WebSession` 自己的主页根与历史，不跨窗口复用返回状态；
   普通与无痕 Profile、窗口顺序、presentation、下载、用户脚本和 AI 共用合同保持不变
5. 增加主页 URL 等价、重定向后主页根、直接目标窗口、历史优先、主页后退出和底栏可用性测试，
   再运行正式开发门禁、差异检查、相关 JVM 测试及规定的 Debug APK 构建与制品核验

本地证据：相关 JVM 测试共 `94` 项通过，主代码 Kotlin 编译、formal readiness、工作树
Markdown 链接、`git diff --check` 与新增代码禁用兜底扫描通过。规定的
`:app:assembleDebug --no-daemon --console=plain` 构建成功，并通过唯一桌面入口与 Player
runtime packaging 校验。Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，
`465765311` 字节，SHA-256
`7157434F8938FCB4BD4608098948086C580CC1FDCB5B8910CD7490EF1DF3A732`；
包名/版本为 `com.kiyori / 45 / 0.1.0`，V2 Debug 签名、单 signer 与
`zipalign -c -P 16 4` 通过。

真机上的系统 Back、顶栏/底栏按钮、普通/无痕多窗口切换、主页重定向和旋转恢复仍需设备验收，
本地实现与 Debug APK 不能替代这些交互证据。

## 2026-07-31 Kiyori 项目架构与 Operit 命名重构方案 v3

方案 v3 已于 2026-07-31 获得实施授权。当前方案让 Kiyori 产品代码与
Operit AI 兼容代码形成两个明确包根，先建立依赖边界和数据保护门禁，再按小里程碑迁移。
v3 进一步把备份范围纠正为当前仓库和本机开发状态，不涉及手机、模拟器或 ADB；同时增加
M-01 精确影响清单，并在第一个应用源码里程碑前增加通用架构/稳定合同门禁基线。

方案入口：

- [Kiyori 项目架构与 Operit 命名重构](kiyori_architecture_refactor/index.md)
- [当前架构与命名分类账](kiyori_architecture_refactor/1_current_architecture_and_naming_ledger.md)
- [目标包结构与依赖规则](kiyori_architecture_refactor/2_target_package_architecture.md)
- [分阶段迁移顺序](kiyori_architecture_refactor/3_migration_sequence.md)
- [Operit AI 上游同步策略](kiyori_architecture_refactor/4_upstream_sync_strategy.md)
- [开发数据、备份与回滚](kiyori_architecture_refactor/5_data_backup_and_rollback.md)
- [验证矩阵与批准门禁](kiyori_architecture_refactor/6_validation_and_approval_gate.md)
- [源码所有权与文件迁移矩阵](kiyori_architecture_refactor/7_file_ownership_and_migration_matrix.md)
- [兼容合同与稳定标识清单](kiyori_architecture_refactor/8_compatibility_contract_inventory.md)
- [工作区、基线与备份作战手册](kiyori_architecture_refactor/9_workspace_preflight_and_backup_runbook.md)
- [里程碑执行模板与首批规格](kiyori_architecture_refactor/10_milestone_execution_template.md)
- [风险登记与停止条件](kiyori_architecture_refactor/11_risk_register_and_stop_conditions.md)
- [最终批准与实施就绪清单](kiyori_architecture_refactor/12_approval_and_implementation_readiness.md)
- [架构门禁与机器可读所有权规范](kiyori_architecture_refactor/13_architecture_guard_specification.md)
- [验证命令目录](kiyori_architecture_refactor/14_validation_command_catalog.md)
- [M-01 Application 原包改名精确影响清单](kiyori_architecture_refactor/15_m01_application_rename_exact_manifest.md)
- [M-00 权威文档同步精确清单](kiyori_architecture_refactor/16_m00_document_authority_update_manifest.md)
- [M-02 Application 全局访问平台化精确清单](kiyori_architecture_refactor/17_m02_application_platform_access_manifest.md)
- [M-03 KiyoriApplication 包迁移精确清单](kiyori_architecture_refactor/18_m03_application_package_move_manifest.md)
- [M-04 根组合与 Shell 精确实施清单](kiyori_architecture_refactor/19_m04_root_composition_and_shell_manifest.md)
- [M-05 Design 与 Platform 精确实施清单](kiyori_architecture_refactor/20_m05_design_and_platform_manifest.md)
- [Stage 4 前质量债务与开发就绪精确清单](kiyori_architecture_refactor/21_quality_debt_and_stage4_readiness_manifest.md)

当前状态：`M-05 complete / M-05E sealed / QD-01..QD-07 complete /
local validation complete / delivery audit in progress`。current-only Lint 已从
`27 errors / 287 warnings / 3 hints` 收敛为 0；依赖、资源、平台、WebKit、TLS、
receiver、权限、accessibility、native asset、PTY 与 Kotlin 编译器警报均按
[Stage 4 前质量债务与开发就绪精确清单](kiyori_architecture_refactor/21_quality_debt_and_stage4_readiness_manifest.md)
分批处理。最终完整 baseline 为 `retained=5606 / stale=92 / current-only=0`，SHA-256
`BEC89B4BF52DE60D7E839336080878B03DB154A7DC072D3C1875BDF0DD1748D0`；未新增
suppress、lint disable 或 baseline 条目。552 条项目 Kotlin 编译器警报的实现清理及本地
最终验证已完成：强制完整 Kotlin 编译项目 warning 为 0，Python `174/174`、architecture
`37/37`、JVM `140 suites / 831 tests`、formal readiness、Markdown、diff 与正式 Lint
均通过。RenderX 依赖错误发布的第二个 `LatexView` launcher 已在 Manifest merge 边界
精确移除，并新增最终合并 Manifest 唯一 launcher 门禁。Debug APK 为
`463718677` bytes，SHA-256
`768CAE74E27352DEE038AF0C4C9F8EE5E3C2C97B017B0F3C2BCD3E92080AEF6D`；
`aapt` 只报告唯一 `MainActivity` 桌面入口。当前进入 Git/远端交付封板。
正式实施严格按
“本地备份与安全点 -> M-00 -> G-00 -> M-01 -> M-02 -> M-03 -> M-04”串行推进；
M-00、G-00 和 M-01 的门禁、测试和 Debug APK 验证均已通过；
后续 G-00 加固已关闭 fresh-clone、Java static import、完全限定项目引用和重复
Manifest component 检查缺口，并扩展 Manifest、持久化、AIDL、Room 与 ObjectBox
合同覆盖；M-02 已把 13 个 concrete Application 消费者收口到四个窄 platform 合同，
`ARCH017` 同时阻止残留 concrete dependency、第二 process owner 和 Service Locator。
完整 Python 为 `107/107`，完整 JVM 为 `773/773`，Debug APK、v2 签名、16 KB 对齐与 player
native packaging 均通过。

M-03 已把唯一 Application 移到 `com.kiyori.app.KiyoriApplication`，Manifest 改用绝对类名，
并只为原同包 `ActivityLifecycleManager` 增加必需显式 import。迁移后的 43 个 Operit 装配
import 由一个文件精确、M-07 到期的 ARCH004 exception 暂存，并由 ARCH018、import snapshot
与规范化源码哈希锁死；Python `109/109`、JVM `773/773`、Debug APK、v2 签名、16 KB 对齐和
player native packaging 均通过。下一独立里程碑为 M-04 根 Composable/App Shell 拆分。
namespace、数据、协议、UI 行为、功能和 terminal 未改变；未使用设备，未提交或推送。

M-04A1 已完成 Operit host CompositionLocal 合同拆分。M-04A2 已完成
`OperitApp -> com.kiyori.app.KiyoriApp` 的纯移动与改名；M-04B1 已把 8 个 AI
route/stack policy 符号移入 `com.kiyori.integration.operit.navigation`。M-04B2 已把
Browser exit presentation enum 移入 `com.kiyori.capability.browser.presentation`，并把
唯一 `KiyoriShellState` / Back owner 移到 `com.kiyori.app.shell`，旧 state 路径已删除。
root 的过渡 Operit import 从 45 降到 35；ARCH022/ARCH023 锁定两份源码 SHA、唯一 owner、
三处 capability 消费、9 个 root Shell import 与四处 Operit 文件级过渡桥。完整 Python
`117/117`、JVM `773/773`、formal readiness、Debug APK 与制品审计均通过；APK SHA-256
为 `04C7BEE47A784B8A3C142BD0B614AB76A2EAE013CCEDE78E89D8FC6EDDA4C055`。
M-04B3 已把唯一 `KiyoriAppShell` host 纯移动到 `com.kiyori.app.shell`；548 行 host 的
规范化源码哈希与移动前完全一致，17 个 Operit import、12 个唯一 host/helper owner、
唯一 root call 和测试接线由 ARCH024 锁定。完整 Python `119/119`、JVM `773/773`、
Debug APK 与制品审计均通过；APK SHA-256 为
`F7FAE589513E46A4EF4D955293D3DE2279C810DC61B117D2B2CFB61FED36B7D7`。
M-04B4 已把唯一 Modal AI Drawer host 纯移动到 `com.kiyori.app.shell`；642 行 Drawer
规范化源码与移动前完全一致，13 个 Operit import、唯一 Drawer/tone owner、App Shell
唯一挂载和测试接线由 ARCH025 锁定。完整 Python `121/121`、JVM `773/773` 与 Debug APK
审计通过；APK SHA-256 为
`00E0DCDF8FEB80BA1382DBF4ADA509CAC24F17105A738F60F677BB9AD9F0FCD5`。
M-04B5 已把 primary destination visual、root dispatch、bottom navigation 和三条动画
策略提取到 `com.kiyori.app.shell.KiyoriPrimaryNavigation`；旧混合文件从 1234 行缩减为
912 行，Software Home 与 Browser 搜索未移动。ARCH026、完整 Python `123/123`、
JVM `773/773` 与 Debug APK 审计通过；APK SHA-256 为
`75C53A4031FE0DFBA3A0A81008A66945B69B55E5B1BB439BBFC327324DF23E37`。
M-04B6 已把 Software Home 页面、四个策略 enum、19 个固定视觉参数、四个纯 resolver、
天气/Browser 窗口动作和 Search/AI frame helper 提取到
`com.kiyori.app.shell.KiyoriSoftwareHome`；旧 `KiyoriShellPages.kt` 从 912 行缩减为
161 行，只剩 Full-Screen Browser Search、request model 与 resolver。ARCH027、完整
Python `125/125`、JVM `773/773` 与 Debug APK 审计通过；APK SHA-256 为
`E4DDCF661685E0A49A0010E2BF27110312403C477503E647FBFE08391288A040`。
M-04B7 已把 residual Full-Screen Browser Search 页面、request model 与 resolver 提取到
`com.kiyori.app.shell.KiyoriBrowserSearch`，并删除旧 `KiyoriShellPages.kt`。ARCH028、
完整 Python `127/127`、JVM `773/773` 与受影响 Browser/Shell/Activity JVM 测试通过；
Debug APK 审计通过，SHA-256 为
`F104A5778C17FA518350FA22420E1073DF1A5FDC0540BF0119C7D3CDD8BBD0BF`。
M-04C 已把 `AppRouteCatalog`、唯一 PackageManager-backed navigation revision、ToolPkg
runtime listener、AppRouterGateway/AppRouteDiscoveryGateway lifecycle 和两条 route-root
helper 收口到 `com.kiyori.integration.operit.navigation`；旧 catalog 路径已删除，
KiyoriApp 的 Operit imports 从 32 降到 25。ARCH029、Python `129/129`、JVM `775/775`
和 Debug APK 审计通过；APK SHA-256 为
`71A860FF9957FCAAB290669449159F8269F145C6C904FB94E8E2E3E0942FC402`。M-04D1 已把
MainActivity 内原先分散的 shared files/text、Browser URL/request ID、GitHub OAuth URI、
shortcut、route、Shell destination 与 current-main-navigation 状态收口到唯一
`com.kiyori.app.startup.KiyoriMainPendingRequests`。Activity 仍负责 Intent 解析、时间戳、
下载、OAuth、分享和 Compose host 副作用；ARCH030、Python `131/131` 与 3 条新 JVM
合同测试已通过。完整 JVM `778/778`、formal readiness 与 Debug APK 审计也已通过；
APK SHA-256 为
`3C0FA7301EF846C90D24CCCB0A4A0492D3A2E7374F9B24182640B17A89423F96`。当前进入
M-04D2，提取纯 Intent decoder，不移动 Android 副作用或创建第二 request store。
M-04D2 已新增 `KiyoriMainIntentDecoder`、密封 command 和稳定常量合同；Activity 不再
直接读取 long/string/parcelable/data payload，但仍在原顺序执行 player、download、
Shell、settings、route、OAuth、Browser 与 share 副作用。ARCH031 与 Python `133/133`
已通过。完整 JVM `783/783`、formal readiness 与 Debug APK 审计也已通过；APK
SHA-256 为
`E4F53F4646191A935453252D8B0ACF0093F46F1F609648B437462771018B411F`。当前进入
M-04D3。M-04D3 已把 sustained performance、API 30+ display mode、API 23–29 refresh
rate 与 hardware acceleration 移到无状态 `KiyoriMainDisplayCoordinator`；MainActivity
只保留一次 configure 调用。ARCH032、Python `135/135` 与 3 条刷新率策略测试已通过，
完整 JVM `786/786`、formal readiness 与 Debug APK 审计也已通过；APK SHA-256 为
`53038237D9B31087CBE6BB1172B7943E988F62809366AA3C2798C716EE848E16`。当前进入
M-04D4。M-04D4 已把 pending external files/text 转交、日志、Toast 和成功/失败清理移到
生命周期绑定的 `KiyoriMainSharedContentCoordinator`；既有 `SharedFileHandler` StateFlow
owner 未移动或复制。ARCH033、Python `137/137`、3 条纯转交决策测试、完整 JVM
`789/789`、formal readiness、lint baseline normalization、Markdown links、
`git diff --check` 与 Debug APK 封板均通过；APK 大小为 `470045143` bytes，SHA-256 为
`AE11A63373B55A7B7A65F59C07C00886ECFEF1D23083CED92CC8D96C498DFC45`。当前进入
M-04D5。M-04D5 已把最近任务可见性恢复及其 API/AI foreground-runtime 判定移到无状态
`KiyoriMainTaskVisibilityCoordinator`；MainActivity 只保留原两个调用时机，不再直接依赖
`ActivityManager`、`AIForegroundService` 或 `setExcludeFromRecents`。ARCH034 失败优先
正反向测试、3 条纯判定 JVM 测试、完整 Python `139/139`、完整 JVM `792/792`、完整
architecture、formal readiness、lint、Markdown、diff 与 Debug APK 封板均通过；APK
大小为 `470045623` bytes，SHA-256 为
`F246315DBCCF89522C7AB2C14EA96E67234A79D8F76B84A05F5352409AE743BB`。下一独立切片为
M-04D6。M-04D6 已把 last orientation、dialog visibility、纯状态转换与方向确认对话框
移到唯一 `KiyoriMainOrientationCoordinator`；Activity 仍按原顺序先隐藏 Plugin Loading，
再分发 configuration change，并继续唯一执行 `recreate()`。ARCH035 正反向测试、4 条
方向行为 JVM 测试、完整 architecture、Python `141/141`、完整 JVM `796/796`、formal
readiness、lint、Markdown、diff 与 Debug APK 封板均通过；APK 大小为 `470049571`
bytes，SHA-256 为
`8E8BA68316543E9685743C3C0D893CC74484E3C2243B0DA71F103195C4209149`。当前进入
M-04D7。M-04D7 已把启动阶段 `POST_NOTIFICATIONS` launcher、API 33 判定、rationale、
request 与 result Toast 移到唯一 `KiyoriMainNotificationPermissionCoordinator`；该
coordinator 作为 Activity 直接字段构造，保持 `registerForActivityResult` 在 onCreate 前
注册。ARCH036 正反向测试、4 条纯决策 JVM 测试、完整 architecture、Python `144/144`、
完整 JVM `800/800`、formal readiness、lint、Markdown、diff 与 Debug APK 封板均通过；
APK 大小为 `470051071` bytes，SHA-256 为
`555D0222EC1DDF9B58C69E1A6EF4CE931CADD4BDCC32C1BC157B9A47625E0D0B`。封板时重新生成
完整 lint 结果并通过结构化交集删除 `51` 条失效历史记录，保留 `5792` 条且不吸收
`328` 条 current-only 问题；ARCH018 同步锁定当前 Application lint 路径为旧 0、新 5，
并新增防止失效第 6 条回流的反向测试。M-04D8 已把 agreement / permission guide /
content 三态决策、唯一 `showPermissionGuide` UI 投影与页面分发迁入
`KiyoriMainStartupGateCoordinator`；`AgreementPreferences` 与
`AndroidPermissionPreferences` 继续唯一持有持久事实，Activity 继续执行 300 ms 延迟、
插件加载、lifecycle 与内容 host 副作用。ARCH037 正反向夹具 2/2、定向 JVM 7/7、完整
architecture、Python `146/146`、完整 JVM `807/807`、formal readiness、lint baseline
normalization、255 个 working-tree Markdown 文件与 `git diff --check` 均通过；Debug APK
大小为 `470053617` bytes，SHA-256 为
`1F5835C63B6946182E3F004EF5EC75CC930E9C8F54A380C67237E9587600DDDF`，包名、版本、
Application、稳定 launcher、多进程、native packaging、v2 Debug 签名与 16 KB 对齐均保持。
主机文件系统 mtime 报告未来值 `2026-08-02 00:53:05 +08:00`，与权威任务日期
`2026-08-01` 不一致，因此只作为本机时钟异常记录，不作为里程碑完成日期。当前进入
M-04D9。该切片已新增一次性 content request projection 与唯一
`KiyoriMainContentHost`，按原顺序执行 pending shared-content 交接、
`LocalPluginLoadingState` provider 与唯一 `KiyoriApp` 挂载；未复制 pending/plugin 状态，
也未移动 `setContent`、主题、startup gate、插件启动、方向对话框或 lifecycle。ARCH020、
ARCH030、ARCH033 已同步锁定新的 content-host owner，ARCH019 至 ARCH038、完整 Python
`148/148`、完整 JVM `134 suites / 810/810`、formal readiness、lint baseline 结构化交集、
255 个 working-tree Markdown 文件、`git diff --check` 与 Debug APK 封板均通过。APK 大小
为 `470058476` bytes，SHA-256 为
`A568EBDF498CD531E3E799BBD6A763E36269E6C1008A524F45B8E5B510ABE26F`；包名、版本、
Application、稳定 launcher、多进程、player native packaging、v2 Debug 签名与 16 KB
对齐均保持。严格 `:app:lintDebug` 在 D9 时暴露既有未基线化债务 `31 errors /
289 warnings / 8 hints`；D9 source/test current-only 为 0，MainActivity 仅有既有
`AppBundleLocaleChanges` warning，未把问题吸收进 baseline。

M-04E 已完成。E1 为稳定 Manifest launcher
`com.ai.assistance.operit.ui.main.MainActivity` 建立精确 compatibility owner，删除唯一
到期 ARCH001 文件例外，并由 ARCH039 锁定精确 ownership 优先级、稳定
package/FQCN/MAIN/LAUNCHER、32 条项目 import、`com.kiyori.feature` 零依赖和唯一
content-host 接线；精确文件 record 覆盖宽泛目录 owner，重叠宽泛 glob 仍必须失败。
E2 只修复 M-04 owner 内有明确行为等价依据的 API/Compose lint：primitive Long state、
API 30/33 边界和 Compose resource 读取。严格 lint 降至 `27 errors / 287 warnings /
2 hints`，M-04 owner 只剩已明确转入 M-05/发布策略的两条 warning，M-04 errors/hints
均为 0。最终结构化交集为 `5792 retained / 0 stale / 316 current-only`，baseline
SHA-256 保持
`A71AB39486275083A30C1DB51F0533162E2DB9054BE8D00BAD1EB2878FE8CE4C`。

M-04 总封板已通过 ARCH019 至 ARCH039、完整 Python `152/152`、完整 JVM
`134 suites / 810/810`、formal readiness、255 个 working-tree Markdown 文件、
`git diff --check` 和 Debug APK 制品审计。APK 为 `468983182` bytes，SHA-256 为
`81F6BA9436031AB20CBFB30C23F111A927FF0913D422A4BC602BBDD1CDEC38D5`；
package/version/label、SDK、Application、稳定 launcher、`:crash/:repair/:player`、
arm64 53 个 native、零重复 basename、player packaging、Debug v2 签名与 16 KB 对齐
全部保持。当前进入 M-05 design/theme/platform：先冻结颜色、Typography、Shapes、
system-bar/edge-to-edge、日志、生命周期、权限和路径合同，再建立失败优先门禁，禁止整体
搬迁 `util`、复制偏好/权限状态或创建万能 platform registry。后续继续按 Browser、Player、
Settings、Files、Mini App、Backup/Recovery、Download settings 和 Android system
surfaces 顺序推进。

M-05 只读 owner 审计已完成并形成
[精确实施清单](kiyori_architecture_refactor/20_m05_design_and_platform_manifest.md)。
旧 theme 目录混合纯 Kiyori design、偏好读取、system-bar、AI 字体和 glass，禁止整体移动。
M-05A1 已建立三个纯 `com.kiyori.design.theme` owner，迁移固定 ColorScheme、Browser theme
与 Settings theme，删除两个旧 theme 文件，并让旧 `ThemeColorSchemeResolver` 只保留偏好
决策 adapter。ARCH040 缺失源文件的 failure-first 证据、正反向 fixture、初始 11 个生产消费者
均已实现；2026-08-09 浏览器文字大小与网站密码管理两个设置子页继续复用同一设置主题，
精确 consumer 合同同步增至 13 个。
新旧测试分工与 ownership 许可均已实现；ARCH024 的 App Shell hash 和完整项目 import
snapshot 已同步到批准后的 design import。封板验证通过完整 architecture、Python
`154/154`、JVM `135 suites / 810 tests`、formal readiness、lint 交集
`5792 retained / 0 stale / 316 current-only`、A1 影响文件 `0` lint issues、256 个
working-tree Markdown、`git diff --check` 与规定的 Debug 构建。最终构建为
`233 actionable tasks / 28 executed / 205 up-to-date`，APK 为 `471291682` bytes，
SHA-256 `B8CD99D49D3F93745282C4E04F7FD7C158A875BE8897FF17161EC4D2C7F93646`；
package/version/label、SDK、Application、稳定 launcher、多进程、arm64 53 native、零重复
basename、Debug v2 与 16 KB 对齐全部保持。M-05A1 已封板。M-05A2 已把纯 semantic
enum/data/color resolver 与 Compose `MaterialTheme` adapter 拆为两个
`com.kiyori.design.theme` owner，删除旧 owner 且不保留 facade/typealias/fallback；54 个生产
消费者、4 个测试消费者、102 条 import 与一处完全限定引用已精确迁移。ARCH025/026/027/
040/041、完整 architecture、Python `156/156`、JVM `135 suites / 810 tests`、
formal/fresh-clone readiness 均通过。新鲜 full lint 仍报告仓库既有 `27 errors / 287 warnings /
2 hints`，但两个新 design 文件为 0 命中，唯一影响路径命中来自
`WebSessionUserscriptSheet.kt` 未改正文；baseline 交集保持
`5792 retained / 0 stale / 316 current-only`。规定 Debug 构建为
`233 actionable tasks / 28 executed / 205 up-to-date`；APK 为 `471292358` bytes，
SHA-256 `60D613A5F9BFBD019296E21E05547DF16B0789FCDE73FF2699AFBAC415A8BBC0`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、零重复 basename、Debug v2 和
16 KB 对齐全部保持。M-05A2 已封板。M-05A3 已把旧 `Theme.kt` 拆为纯
`com.kiyori.design.theme.KiyoriTheme/KiyoriTypography`、唯一
`com.kiyori.app.theme.KiyoriTheme` 偏好/字体/Glass host 和唯一
`com.kiyori.platform.window.KiyoriApplicationSystemBars`；旧 `Theme.kt`、`OperitTheme`、
`Theme.Operit` 与旧 root-theme test 已删除，不保留 facade、typealias、fallback 或第二 owner。
MainActivity 与桌面 Widget 配置 Activity 复用同一 app host，6 个 style 声明和 Manifest
6 个引用均为 `Theme.Kiyori`；Type 只保留配置字体、文件 I/O、日志和 AI 局部字体适配，
Liquid/Water Glass 算法与 PlayerActivity fullscreen system-bar 字节不变。ARCH040/041/042、
完整 architecture `phase=m03`、Python `158/158`、JVM `134 suites / 810 tests`、
formal/fresh-clone readiness 与 `git diff --check` 通过。fresh full lint 保持既有
`27 errors / 287 warnings / 2 hints`，四个新 owner 为 0 命中，baseline 交集保持
`5792 retained / 0 stale / 316 current-only`。规定 Debug 构建为
`233 actionable tasks / 28 executed / 205 up-to-date`；APK 为 `477957302` bytes，
SHA-256 `9373518AEB8FA8BD2C02DCFDE5741833653A2D76AFE270D2C27F4D2BF88C0074`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、零重复 basename、Debug v2 和
16 KB 对齐全部保持。M-05A3 已封板。M-05B 也已封板：唯一
`KiyoriLogger/KiyoriLogTextFormatter` owner、旧 `AppLogger` 无状态 facade、8 个 Kiyori
app consumer、Provider/Application 目录绑定、旧 static-mock 和 ARCH043 均已闭环。完整
architecture `phase=m03`、Python `160/160`、JVM `135 suites / 813 tests`、
formal/fresh-clone、Markdown 和 diff 通过。full lint 保持既有
`27 errors / 287 warnings / 2 hints`；新 owner 为 0 命中，baseline 只删除旧
`AppLogger.kt` 已失效的 `StaticFieldLeak`，交集为 `5791/0/316`，SHA-256 为
`AEB75AEE42CF985AC5A8F6A33DE88C04D1B0C50580E952E32542FE8403AAB8C0`。规定 Debug
构建为 `233 actionable tasks / 24 executed / 209 up-to-date`；APK 为 `477957302`
bytes，SHA-256 `3A9BCBC711DB1FB735C3828AA1751F80B347C8C1FC7E34E783F16FFD96D631A1`，
身份、SDK、Application、稳定 launcher、多进程、arm64 53 native、10 个播放器目标库、
零重复 basename、Debug v2 与 16 KB 对齐通过。M-05C 已新增唯一
`KiyoriActivityLifecycle` callback/facts owner 与
`OperitActivityLifecycleIntegration` side-effect owner，旧 `ActivityLifecycleManager`
保留完整 JVM ABI 并成为无状态 facade；13 个旧 FQCN consumer、Application 初始化位置、
plugin/AI/Player/窗口行为保持。ARCH044 failure-first、正反向 fixture、真实与完整
architecture `phase=m03`、Python `162/162`、JVM `136 suites / 817 tests`、
formal/fresh-clone、Markdown 和 diff 通过。fresh full lint 保持既有
`27 errors / 287 warnings / 2 hints`，M-05C 四条路径为 0 命中。规定 Debug 构建稳定为
`233 actionable / 24 executed / 209 up-to-date`；APK 为 `477957302` bytes，SHA-256
`3792DD58C87D1DDD4F977BB8CD4B4407458EB911EC17CB0CB48CB8876184D2D3`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、10 个播放器目标库、Debug v2 与
16 KB 对齐保持。M-05C 已封板。M-05D 已完成唯一
`KiyoriNotificationPermissionCapability`、无状态 Operit resource bridge 与旧 app
coordinator projection 实现；旧 coordinator 的一参数构造与 `checkAndRequest()` javap
逐项保持，MainActivity 早注册/单调用、6 条日志、2 个 Toast、Manifest 声明和两个非启动
通知检查保持。ARCH045 failure-first、ARCH036/045 正反向 fixture、真实/完整 architecture、
生产编译、完整 Python `164/164`、完整 JVM `136 suites / 817 tests`、formal/fresh-clone
readiness、Markdown、diff、lint baseline normalization、敏感签名与规定 Debug 构建均通过。
fresh full lint 保持既有 `27 errors / 287 warnings / 2 hints`，M-05D 四条路径为 0 命中。
APK 为 `477957302` bytes，SHA-256
`BB370BC2602880CA4DCE488F67DA4AB9F105C1CFF07A0E4D2FF31A3D3CC38B34`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、10 个播放器目标库、Debug v2 与
16 KB 对齐保持。M-05D 已封板。M-05E 已建立唯一
`com.kiyori.platform.storage.KiyoriPaths` 与纯 `KiyoriBackupPaths`，把旧
`OperitPaths` / `OperitBackupDirs` 收口为完整 ABI 兼容 facade，并按精确边界迁移
`9 / 7 / 37 / 0` consumer 集合。ARCH046 failure-first、正反向 fixture、最终完整
architecture、Python `166/166`、JVM `137 suites / 822 tests`、formal/fresh-clone、
Markdown、diff、敏感审计与 fresh lint 通过；lint 保持既有
`27 errors / 287 warnings / 2 hints`。规定 Debug 构建和 APK 审计通过，APK 为
`477957302` bytes，SHA-256
`E6A5E78CFB4441399DC36D83583BF6F441441E3736A85A17759695D73ACF790C`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、10 个播放器目标库、Debug v2 与
16 KB 对齐保持。M-05E 已封板，M-05 design/theme/platform 阶段完成。设备/UI、
多进程文件写入、真实日志导出、真实 lifecycle side effect、Android 13+ 权限框/通知到达、
路径读写与备份恢复验收继续为 `verification_pending`。

## 2026-08-02 Stage 4 前质量债务与开发就绪收口

M-02 至 M-05 已形成可重现 checkpoint
`6b6493a0bfd12072116e45fb733d551fad13e32b`，该提交通过 fresh clone 和 formal
readiness。阶段 4 Browser 产品域开始前，先按
[质量债务与开发就绪精确清单](kiyori_architecture_refactor/21_quality_debt_and_stage4_readiness_manifest.md)
收口初始 `27 errors / 287 warnings / 3 hints`。QD-01 至 QD-07 的实现清理已完成；
最终完整 baseline 保留 `5606` 条历史记录，结构化交集
`stale=92 / current-only=0`，高风险正确性和安全项已完成根因修复或进入独立设备/发布验收。

执行顺序与当前状态：

1. [DONE] current-only correctness、KTX/SDK/Compose、资源/plurals 与平台合同
2. [DONE] 依赖升级、第三方不安全字节码净化与 compile SDK/Kotlin 对齐
3. [DONE] 552 条项目 Kotlin 编译器警报实现清理
4. [DONE] TLS/WebView/receiver/权限/accessibility/native/PTY 高风险债务
5. [DONE] baseline 交集剪枝：`5606 / 92 / 0`
6. [DONE] 强制完整编译、全量测试、架构/正式门禁、Debug APK 与敏感审计
7. [IN PROGRESS] 审计提交范围并按授权提交、推送和核对远端 ref

禁止通过 suppress、扩大 baseline、关闭 dependency lint、fallback 或行为不明的批量删除
取得表面全绿。设备和 Release 验收继续独立记录。

## 2026-07-31 插件中心信息架构与日志交互优化

本轮继续复用同一个 Browser Plugin Center、`UserscriptRepository` 和
`WebSessionUserscriptManager`，修正插件中心重复展示和日志不可操作的问题。

实施与验收门禁：

1. [DONE] 审计本页、油猴脚本、插件库、日志和浏览器设置的入口与状态所有权
2. [DONE] 插件中心“本页”改为 provider-only；油猴脚本条目只在油猴脚本工作区显示
3. [DONE] 插件库从独立页签迁移到右上角加号，保留 Greasy Fork、ScriptCat、OpenUserJS、
   Userscript.Zone 和 GitHub 五个已核实 HTTPS 来源
4. [DONE] 日志支持单击详情与复制、长按直接复制、复制全部和导出全部；完整保留日志提升到 200 条
5. [DONE] 设置页拆分插件中心、油猴脚本管理、权限与网站范围、当前页诊断和脚本日志入口；
   权限行直接打开对应脚本详情
6. [DONE] 定向测试 `244/244` 通过，零失败、零错误、零跳过
7. [DONE] 更新正式文档、formal readiness、最终 Debug APK 和产物核验
8. [PENDING] 目标 Android WebView 真机复测窄屏、长按、日志弹窗、复制、导出和设置路由

当前非目标：

- 不创建第二 Browser Runtime、第二 userscript 仓库或第二日志状态源
- 不把长按日志设计成重复的二级菜单；长按只作为直接复制快捷操作
- 不提交、不推送、不安装设备

## 2026-07-31 真实 userscript 异常与浏览器插件设置收口

本轮继续复用同一个 Browser Plugin Center、`UserscriptRepository` 与
`WebSessionUserscriptManager`。用户提供的 vivo Android 16 截图显示，“轻小说文库+”
`2.31.2` 已在 wenku8 阅读页进入执行阶段，实际状态是异常而不是未命中：
`GM_info.script` 读取到了 `undefined`。真实脚本源码同时证明其三组
`http*://*.wenku8.com/.net/.cc/*` 匹配规则已经生效。

实施与验收门禁：

1. [DONE] 获取 Greasy Fork 脚本 `539514` 当前真实源码、metadata、GM API 使用集合和设备错误堆栈
2. [DONE] 定位 `GM_info` 根因：bootstrap 只在显式 `@grant GM_info` 或 `@grant none` 时创建信息对象
3. [DONE] 让 `GM_info` 与 `GM.info` 对所有 userscript 提供同一个只读脚本信息对象，同时不扩大
   native host 权限
4. [DONE] 为 `http*://*.wenku8.com/.net/.cc/*` 和脚本实际 grants 增加真实样本回归测试
5. [DONE] 重构网页浏览器设置的第一组，接通“允许用户脚本”“网页插件管理”“插件权限与网站范围”
   和“当前页脚本诊断”
6. [DONE] 设置页只观察现有 userscript 状态流；总授权继续写入现有 registry，不建立第二设置 owner
7. [DONE] 权限页显示 runtime 支持状态、安装/启用数量、每个脚本的执行世界、grant、`@connect`、
   页面范围、未知权限和阻塞原因；脚本声明权限不伪装成可单项开关
8. [DONE] 更新语义文档，执行 userscript 与设置页定向测试、Kotlin 编译、formal readiness、
   `git diff --check` 和最终 Debug APK 构建核验
9. [PENDING] 目标设备安装后复测“轻小说文库+”启动、菜单、存储、资源、GM XHR 与设置入口；
   本轮未获设备操作授权，不安装 APK

本轮新增本地证据：

- userscript runtime、matcher、metadata、storage、插件中心、浏览器 Back 和设置页相关定向测试
  共 `86/86`，零失败、零错误、零跳过
- `python -B ci/script/check_formal_readiness.py --repository . --require-main` 与
  `git diff --check` 通过；差异检查仅报告工作树既有 CRLF 到 LF 提示，没有 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 51s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 21:46:40 +08:00`，大小 `494139942` 字节，SHA-256
  `2956E71105E5672D2F28F61AD341E11407CA82302FEF53467E646DC86D5948F3`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、arm64-v8a；Android Debug V2
  签名与 `zipalign -c -P 16 -v 4` 均通过

当前非目标：

- 不新增第二 Browser Runtime、userscript 仓库或浏览器设置状态源
- 不把脚本声明的 grant 改造成与真实授权模型不一致的逐项开关
- 不承诺未实测的全部第三方脚本兼容，不实现 `.kbx` 或 WebExtension runtime
- 不提交、不推送、不操作设备

## 2026-07-31 浏览器插件中心二次 UI、编辑器与 userscript 匹配诊断

本轮在既有 Browser Plugin Center、`UserscriptRepository` 与
`WebSessionUserscriptManager` 上进行第二次深度收口。目标是修复插件编辑器的系统栏遮挡和
底部操作区过高，消除把“没有当前页面状态”误报为“未命中”，并让常见 userscript 匹配、
权限与状态在界面中可解释。Kiyori 当前仍为未发布内部版本，保留此前同一工作树中的未提交改动；
不创建第二 Browser Runtime、第二 userscript 仓库或兼容回退链。

实施门禁：

1. [DONE] 只读审计编辑器根布局、系统 Insets、输入法、浏览器底栏几何和全部插件按钮回调
2. [DONE] 只读审计 userscript 匹配、页面世界/隔离世界状态回报、`@grant`、`@connect` 和
   当前“未命中”推导链
3. [DONE] 确定写码前设计：编辑器 44dp 顶栏、浏览器同款底部动作几何、输入法期间单层底部
   工具、`NO_ACTIVE_PAGE`/`MATCHED` 状态和统一匹配诊断
4. [DONE] 扩展匹配器：`http*`、端口、常见主机通配、正则 flags、`@connect` 子域和可解释
   的命中/排除规则；保持 query/fragment 语义正确
5. [DONE] 收口运行时状态：页面世界已匹配状态、活动页面 URL、无活动页面与未初始化状态，
   不再用空状态猜测 `NOT_MATCHED`
6. [DONE] 收口权限语义：所有脚本都可读取本地只读 `GM_info / GM.info`，而 `@grant none` 不暴露 native/GM 特权 API；
   安装/详情/更新预览分组展示 grant、
   connect、页面范围、执行世界和不兼容原因
7. [DONE] 重构插件中心按钮：概览添加菜单只进入已实现动作；URL 输入校验；更新/安装确认、
   详情和日志按钮状态与真实异步结果一致
8. [DONE] 优化编辑器：状态栏 Insets、44dp 顶栏、更多菜单、紧凑查找替换、浏览器同款底部
   动作栏、输入法期间符号栏单层显示和 Metadata 错误可见
9. [DONE] 修复详情页规则字段显示，分别展示 `@run-at`、`@inject-into`、`@run-in`、
   `@noframes`，并补充相关文案
10. [DONE] 增加匹配器、状态推导、权限和 UI 投影定向测试；执行 `git diff --check`、
    formal readiness 与项目要求的 Debug APK 构建核验
11. [PENDING] 目标 Android 设备上复测状态栏、输入法、窄屏按钮、真实脚本命中与权限行为；
    本轮不在未获设备操作授权时安装或操作设备

本轮最终本地证据：

- userscript、插件中心、编辑器 UI、浏览器 Back 和 runtime 相关定向测试共 `70/70`，零失败、
  零错误、零跳过；测试任务重新执行了 `:app:compileDebugKotlin`
- 七份实际 `strings.xml` 均可解析，名称集合无重复且包含本轮新增文案键；`formal readiness`
  与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：PASS，233 个任务，
  32 个执行、201 个缓存命中；`:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 20:39:03 +08:00`，大小 `494139942` 字节，SHA-256
  `B70F85F46C579419604E3C3870F8296D8E84466F81C2BC13488FE00930BE5324`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、arm64-v8a；Android Debug V2
  签名与 `zipalign -c -P 16 -v 4` 均通过

当前非目标：

- 不实现 `.kbx`、Chrome 扩展包直接安装、新标签页 Provider 或 WebExtension runtime
- 不把未实现的 `@run-in`、`@sandbox`、`@unwrap` 伪装为已支持
- 不提交、不推送、不部署、不覆盖或清理工作树已有改动

## 2026-07-31 浏览器油猴脚本管理闭环与全屏编辑器

本轮在同一个 Browser Plugin Center、`UserscriptRepository` 与 `WebSessionUserscriptManager`
上继续完成 Phase 1 管理闭环，不引入第二 Browser Runtime、第二脚本仓库、WebView 编辑器或回退执行链。
界面继续使用浏览器书签、下载等子抽屉的紧凑标题、搜索、标签、卡片和确认风格；源码编辑器作为浏览器
宿主内全屏原生页面打开。

实施与验收门禁：

1. [DONE] registry v2、应用私有 immutable revision、draft、staging、transaction journal 与原子迁移
2. [DONE] “本页 / 已安装 / 更新 / 日志”四个工作区、脚本搜索、紧凑权限提示与草稿恢复入口
3. [DONE] 脚本详情展示匹配规则、权限、`@connect`、依赖、源码、日志与版本历史
4. [DONE] 已安装脚本长按多选、批量启用、批量禁用与确认后批量删除
5. [DONE] 单项及批量检查更新；检查保持只读，批量应用只提交权限和来源不扩大的安全更新
6. [DONE] 风险更新在应用前展示新增权限、范围变化、来源变化和统一源码差异
7. [DONE] 浏览器内全屏 `NativeCodeEditor` 支持查找替换、撤销重做、格式化和结构化 metadata
8. [DONE] 编辑器私有草稿自动保存、语法/权限检查、差异预览、显式应用与离开决策
9. [DONE] 新建草稿、已安装脚本草稿和中断后未安装草稿均可恢复，不提前覆盖活动 revision
10. [DONE] 定向测试、七语种资源校验、正式开发门禁、差异审查和 Debug APK 构建核验
11. [PENDING] 目标设备验收窄屏四标签、多选、详情/编辑器 Back、草稿恢复、更新审查和真实脚本应用
12. [PENDING] 后续补充 userscript 导出与删除时的脚本数据保留选择

本地验证证据：

- `BrowserPluginCenterFacadeTest`、`WebSessionBrowserBackPolicyTest`、
  `UserscriptStorageTransactionTest`、`UserscriptManagementPolicyTest` 与
  `UserscriptMetadataParserTest` 合计 `23/23`，零失败、零错误、零跳过
- `:app:compileDebugKotlin`、formal readiness 与 `git diff --check` 通过
- 七份 `strings.xml` 均可解析；本轮新增 `76` 个文案键在中文、英语、西班牙语、印尼语、
  韩语、马来语和巴西葡萄牙语中名称集合一致且无本轮重复键
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 `233` 个任务，
  `28` 个执行、`205` 个缓存命中，`:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 19:12:39 +08:00`，大小 `494137206` 字节，SHA-256
  `C65D30791DA9B054428FFAF63169B58407BEE107D25BDDC1540B25A0820C8267`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、compile 36；
  Android Debug V2 签名与 `zipalign -c -P 16 -v 4` 均通过

## 2026-07-31 AI 对话公式编号、方框字形与 Markdown 节点边界修复

本轮只修改 Operit AI 对话已有 Markdown/LaTeX 渲染链，不切换公式引擎，不引入第二套
Markdown AST、WebView 公式渲染器或并行复制链。Kiyori 从未发布，因此直接修正当前内部实现。

实施与验收门禁：

1. [DONE] 为块公式建立共享语义解析，支持顶层 `\tag{...}` 与 `\tag*{...}`
2. [DONE] 公式主体保持居中，编号独立右对齐；窄屏和超宽公式不得重叠
3. [DONE] 流式块公式闭合前不显示 LaTeX 源码，闭合后与静态解析使用同一结果
4. [DONE] 修复 `jlatexmath-android 0.2.0` 的框绘制状态泄漏，使 `\boxed` 与 `\fbox`
   内部及后续字形保持填充
5. [DONE] 复制转换显式区分行内/块级公式，并保留公式编号语义
6. [DONE] 增加解析、复制、绘制状态和消息渲染回归测试
7. [DONE] 执行正式开发门禁、定向测试、`git diff --check` 和 Debug APK 构建核验
8. [DONE] block splitter 在显示公式插件之前识别行内代码，支持不同长度的反引号，
   并把保护片段并回同一段落的 `INLINE_CODE` 子节点
9. [DONE] 引用块每行剥离当前层的一个 `>` 标记，并递归复用同一 block AST，使真实块公式、
   行内代码和围栏代码保持各自语义
10. [DONE] 增加代码 span 中 `\[...\]`、`$$...$$`、多反引号、引用块公式与代码混合回归测试
11. [PENDING] 目标设备复测浅色/深色、流式输出、窄屏编号、复杂方框公式、引用块公式和
    行内代码定界符

本地验证证据：

- 代码 span 分隔符、公式语义与布局、框绘制和批更新定向 JVM 测试 `19/19`，
  零失败、零错误、零跳过
- `:app:compileDebugAndroidTestKotlin` 通过，覆盖 block splitter 代码保护、引用块递归 AST、
  编号复制语义、框命令注册和 Android Paint 状态测试源码
- `:app:externalNativeBuildDebug`、formal readiness 与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 `233` 个任务、
  `29` 个执行、`204` 个缓存命中，`:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 15:40:12 +08:00`，大小 `494083710` 字节，SHA-256
  `A289D5C685385C00AB5E9C54AC6FFA4144AA4DE98DF8CD9DD7226890D7705F51`
- APK 为 `com.kiyori`、`45 / 0.1.0`、compile 36、target 34、`arm64-v8a`；
  Android Debug V2 签名与 16 KB ZIP 对齐通过

## 2026-07-31 用户脚本运行授权、`unsafeWindow` 特权兼容与插件搜索

本轮继续开发浏览器下拉抽屉“插件”按钮内的 Browser Plugin Center，不涉及 AI 对话页 ToolPkg、
Skill、MCP 或包管理。阻塞
`unsafeWindow cannot be combined with privileged grants in the current runtime`
来自旧执行世界策略：特权 grant 必须留在隔离世界，而旧 bootstrap 只能把当前世界的 `window` 传给
`unsafeWindow`，所以策略直接拒绝两者组合。

本轮完成：

1. [DONE] 在现有 userscript registry 中增加持久化“允许用户脚本”总授权，默认关闭，不建立第二状态源
2. [DONE] 插件概览卡与油猴管理页共用同一授权开关；权限关闭时脚本显示“需要授权”，不误报“不兼容”
3. [DONE] 页面与共享隔离运行时按 WebView 生命周期各注册一次；权限关闭时运行时保持惰性、不返回脚本
   payload，并撤销 token、reply proxy、页面菜单、webRequest 与活动 GM 网络请求
4. [DONE] `auto / content` 下的 `unsafeWindow + privileged grants` 保持隔离执行，通过同步
   页面对象桥访问网页原始 `window`
5. [DONE] 页面对象桥不包含 native bridge、authorization token 或 GM 方法；页面只能观察或影响
   `unsafeWindow` 自身的网页对象操作
6. [DONE] 页面对象桥支持属性读写、方法、构造、回调、Promise、`fetch / Response`、普通对象、
   DOM 节点、`in` 与删除，并限制单次请求/响应和单文档引用数量
7. [DONE] 显式 `@inject-into page + privileged grants` 继续判定不兼容
8. [DONE] 安装预览和脚本详情显示页面世界、隔离世界、页面直连或隔离页面对象桥
9. [DONE] 插件中心搜索同时命中内部脚本；油猴管理页本地搜索覆盖名称、命名空间、描述、来源、
   grant、匹配网站、`@connect` 和标签
10. [DONE] 七语种 UI 文案、正式架构、`CONTEXT.md`、根 `README.md` 和浏览器 TODO 同步完成
11. [DONE] 处理 2026-07-31 vivo Android 16 设备报告：移除按脚本创建世界及权限/列表变化时的原生
    handler/listener 动态拆装，收敛为单 WebView 单隔离世界；WebView 关闭时由紧邻的 `destroy()` 退休注册
12. [DONE] 修复“未命中”：无 `@match/@include` 时按全页面匹配，支持正则 include/exclude，
    `@match` 忽略 query/fragment，document-start 使用当前文档 URL 并校验页面世界 `sourceOrigin`
13. [PENDING] 使用修复版 APK 在 Android System WebView 真机验证脚本命中、连续导航、授权启停、
    `unsafeWindow` 跨 world DOM 事件、iframe 和目标 userscript 不再触发主进程 SIGSEGV

本地验证证据：

- 插件/userscript 定向 JVM 测试 `46/46`，零失败、零错误、零跳过
- 真实 Chrome main world / isolated world 测试通过属性、方法接收者、回调、Promise、
  `fetch / Response`、DOM 参数与返回对象、构造、删除及临时 DOM 标记清理
- formal readiness、7 份本地化 XML 与 16 个新增键、34 个 Markdown 相对链接、
  旧阻塞字符串在运行时代码中零引用、页面对象桥敏感能力零引用和 `git diff --check` 均通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 233 个任务，
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 14:01:09 +08:00`，大小 `494083710` 字节，SHA-256
  `21917BD1C1CF80E1B00128A621DABE6CDB9110B56067C6B29331C0F3EA29E90D`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、compile 36；Android Debug V2
  签名通过，`zipalign -c -P 16 -v 4` 为 `Verification successful`

## 2026-07-30 浏览器插件中心与内置油猴脚本里程碑一

本轮正式启动浏览器下拉抽屉菜单“插件”按钮对应的 Browser Plugin Center。它属于浏览器产品域，
不连接 AI 对话页的 ToolPkg、Skill、MCP、工作流或包管理。完整长期合同见
[`browser_plugin_platform.md`](../doc-src/architecture/browser_plugin_platform.md)。
后续 Phase 0 至 Phase 8 的详细任务、顺序和独立验收门禁见
[`browser_plugin_platform/`](browser_plugin_platform/index.md)。

Kiyori 与该入口尚未发布，因此直接清理旧 `USERSCRIPTS` UI 路由；已安装 userscript、仓库、
运行状态和日志继续由现有 `UserscriptRepository` 与 `WebSessionUserscriptManager` 唯一持有，
不得迁移、复制或清空。

里程碑一实施与验收门禁：

1. [DONE] 审计 Kiyori 浏览器抽屉、Back 状态机、userscript manager、安装预览和共享
   `StandardBrowserSessionTools` 调用链
2. [DONE] 只读参考 `D:\10_Project\hikerView` 的统一列表、编辑入口和页面生命周期注入原则，
   不复刻缺失 `JSManager`、旧 Activity 或事件总线
3. [DONE] 固化插件、userscript、未来原生插件、WebExtension 与 AI ToolPkg 的产品和运行时边界
4. [DONE] 固化 AI 生成脚本/插件的草稿、检查、权限审查、用户确认和安装事务
5. [DONE] 新增统一 Browser Plugin 模型与纯投影 facade，不建立第二插件仓库
6. [DONE] 把浏览器抽屉 `USERSCRIPTS` 路由替换为 `PLUGINS`
7. [DONE] 增加 `OVERVIEW / USERSCRIPTS` 子页面，菜单进入概览，脚本安装预览直达管理页
8. [DONE] 建立“本页 / 已安装”、搜索、添加和内置“油猴脚本”插件卡
9. [DONE] 增加经过核实的 Greasy Fork、ScriptCat、OpenUserJS、Userscript.Zone 和 GitHub
   userscript 来源快捷弹窗，在共享 Browser Runtime 新标签打开
10. [DONE] 系统 Back 与标题返回从油猴脚本子页先回插件概览，再关闭下拉抽屉
11. [DONE] 补充插件投影、当前页统计、路由和 Back 策略测试
12. [DONE] 更新 `CONTEXT.md`、根 `README.md` 和浏览器能力资料
13. [DONE] formal readiness、5 项定向测试、7 份 strings 解析、旧路由零引用、
   `git diff --check` 和 Debug APK 构建核验均通过
14. [PENDING] 目标设备验收抽屉拖动、窄屏布局、来源弹窗、新标签跳转、安装预览和返回手势

本轮明确不实现通用 WebExtension runtime、CRX/Edge 扩展直接安装、沉浸式翻译移植、原生插件包协议、
AI 自动安装或 userscript 数据/注入引擎重写。

本地验证证据：

- `BrowserPluginCenterFacadeTest`、`WebSessionBrowserBackPolicyTest` 和
  `WebSessionBrowserUserAgentRoutingTest` 合计 `5/5`，零失败、零错误、零跳过
- 7 份 `strings.xml` 均可解析，新插件文案键完整且无重复；旧
  `WebSessionBrowserSheetRoute.USERSCRIPTS`、`onOpenUserscripts` 和
  `openUserscriptSheetOnMain` 引用为零
- formal readiness 与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 `233` 个任务、
  零失败，`:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482641438` 字节，
  SHA-256 `F69E8FFB6B9EB5E926F7A0EA9BBA43619D7503B87A30B58F8C8468707B283CEC`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34；Android Debug V2 签名和
  `zipalign -c -P 16 -v 4` 通过

## 2026-07-30 浏览器统一历史下拉抽屉与负一屏入口

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器资料库的唯一进度载体。Kiyori 尚未发布，现有仅展示 WebView 当前会话和网页访问记录的
旧历史抽屉直接替换；不保留第二套历史页面、旧双区结构或并行数据库。

实施与验收门禁：

1. [DONE] 扩展 `WebSessionHistoryStore` 的现有 `history_json`，统一承载网页和真实播放器记录
2. [DONE] 网页只由普通 Profile 的主框架访问回调写入，不把网络日志、媒体候选或视频直链重复记为网页
3. [DONE] 唯一 `PlayerSession` 在接受新媒体请求时写入视频历史，并以“在线视频 / 本地视频”标记来源
4. [DONE] 历史持久化不保存 Cookie、Authorization 或完整请求 headers；在线重播在点击时从活动
   WebSession 读取当前 UA、Profile Cookie 和来源页 Referer
5. [DONE] 用网络日志抽屉的紧凑结构重建历史 UI：标题、数量、搜索、删除、横向
   `全部 / 网页 / 视频 / 音乐 / 小说 / 其他` 筛选和分类空态
6. [DONE] 删除动作打开“过去一小时 / 过去24小时 / 过去一周 / 所有时间 / 取消”底部选择面板，
   只删除当前分类与时间范围匹配的记录
7. [DONE] 浏览器菜单和负一屏“历史”卡挂载同一个共享抽屉；负一屏显示真实历史总数
8. [DONE] 补充 store、分类过滤、删除范围、媒体来源和 Shell 状态测试，更新语义文档
9. [DONE] formal readiness、差异检查、5 组定向测试（104 项）和
   `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 已通过，最终 Debug APK 已生成并核验
10. [IN PROGRESS] 用户已确认新版历史记录界面正常；目标设备仍需继续覆盖搜索、抽屉拖动、
    时间范围删除、普通/无痕隔离、网页打开及在线/本地视频重播

## 2026-07-30 AI 抽屉状态徽标、彩色图标与底栏弹性动效

本轮不改变导航路由、业务状态 owner 或页面结构，只修复当前 UI 的明确遮挡并收口三组图标视觉。
Kiyori 尚未发布，因此直接迭代当前样式，不保留旧蓝色选中态或平行图标方案。

实施与验收门禁：

1. [DONE] 定位 AI 左抽屉包管理、权限、工作流卡片的状态徽标与居中图标重叠根因
2. [DONE] 设计状态徽标独立顶部区域，并保留三个快捷入口的现有计数与权限状态语义
3. [DONE] 将软件首页天气图标与 AI 对话页浏览器、终端、工作区三个顶栏动作接入固定语义色；
   晴天独立使用浅色 `#C57C00`、深色 `#FFD166`
4. [DONE] 为底部五个封闭图标增加精确 `#FFC153` 填充层与页面背景色内部细节层，删除蓝色空心选中态
5. [DONE] 按各 Vector 可见边界补偿选中态终点尺寸，并用仅含内部圆环的设置细节层清除外缘残线
6. [DONE] 每次点击先连续压回较小填充态，再以低阻尼弹簧放大到原图标视觉尺寸；重复点击当前入口同样重播
   - [DONE] 后续微调仅将首页、浏览器、小程序和文件入口阻尼降至 `0.42`，扩大黄色峰值；设置保持 `0.55`
7. [DONE] 更新视觉合同和定向测试
8. [DONE] 执行正式开发门禁、差异检查并构建核验 Debug APK
9. [PENDING] 目标设备浅色、深色、窄屏抽屉、五入口最终尺寸与弹性动效独立验收

本地验证证据：

- `KiyoriThemeTest 11/11`、`KiyoriShellStateTest 47/47`，合计 `58/58`，零失败、零错误、零跳过
- Android 资源合并、`:app:compileDebugKotlin`、formal readiness 与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 `233` 个任务、零失败，
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482617522` 字节，
  SHA-256 `22C6A2D7A20D28038DB8B549826F56CA7871F18E79D6748F7D4310FC358AC15A`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34；Android Debug V2 签名与
  `zipalign -c -P 16 -v 4` 通过

## 2026-07-30 Operit `1.12.0+5` 上游差异审计与选择性移植

本轮以 `upstream/main@98faa44c` 为上游目标，Kiyori 以
`main@ce0d504f` 为本地基线。双方共同祖先为 `ef00abc5`；Kiyori 已有 46 个独立提交，
上游有 42 个独立提交，不能通过整分支 merge、最终文件覆盖或版本号追平替代逐项审计。

本轮需要移植的修复：

- OpenAI Responses 重放 reasoning item 时始终携带合法 `summary` 数组
- 对话总结中的包列表明确标记为“已激活包”，避免模型重复调用 `use_package`
- 流式 Markdown 表格更新时保留横向滚动状态，并修复工具调用尾部与等长结构变化漏刷
- 用户中断普通聊天输出后，保留部分内容、Token、等待耗时和输出耗时
- 包管理普通脚本包启停完成后，以真实后端结果同步可见开关状态
- 将 WebChat 的 Vite/esbuild 更新到上游已验证的安全边界

本轮明确不合并的上游范围：

- Operit 主题编辑器与运行时 scoped theme snapshot。Kiyori 固定主题和 AI 局部个性化已有独立
  产品合同，不能恢复上游全局主题所有权
- GitHub OAuth broker、Operit 发布/公告、夜间构建和 `1.12.0+5` 发布元数据
- 罗马尼亚语、STT 构建期联网下载和上游 Lint baseline 删除
- MCP bridge 的 uuid 依赖删除。Kiyori 的 `tools/mcp_bridge/index.ts` 仍调用 `uuidv4()`，
  该依赖不是无用依赖，不能照搬上游删除
- ToolPkg 市场来源标记、聊天消息持久化 Hook 与整套发布协议。对应能力完整接入前，
  `OPERIT_MARKET_COMPAT_VERSION` 在该历史任务中继续保持 `1.12.0+4`，不能虚报 `+5`
  兼容；这些前置能力已由 2026-08-08 的 `+8/+9` 专项任务补齐并将当前基线提升为 `+9`

实施与验收门禁：

1. [DONE] 获取上游并完成提交图、路径重叠、最终源补丁和 Kiyori 当前实现审计
2. [DONE] 手工适配选定修复，不执行 merge/cherry-pick，不覆盖 Kiyori 产品域
3. [DONE] 7 个针对性 JVM 测试通过；Responses 和表格 AndroidTest 已编译通过，
   真机运行单列为设备验收边界
4. [DONE] WebChat Vite `6.4.3` 生产构建成功、npm 审计为 0 漏洞、formal readiness 通过、
   `git diff --check` 通过；同步正式 WebChat 资产后重新构建 Debug APK
5. [DONE] 未移植范围、MCP uuid 差异、Git 状态和 `1.12.0+5` 兼容版本升级条件已记录

本轮最终 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，包名 `com.kiyori`，
`versionCode=45`、`versionName=0.1.0`、仅包含 `arm64-v8a`；V2 签名与 16 KiB page alignment
检查通过。APK 内 `assets/web-chat` 的 HTML、CSS、JS SHA-256 已逐项与
`web-chat/dist` 一致，避免只验证未进入 APK 的旁路构建产物。

## 2026-07-30 视频播放器 UI、手势、旋转与 Anime4K 七档闭环

本轮继续复用唯一 `PlayerSession`、唯一非导出 `:player` MPV runtime、现有 Surface lease、
`PlayerSettingsStore` 和浏览器下载 owner。Kiyori 与当前播放器仍未发布，因此直接清理旧的
“关闭 / 流畅 / 均衡 / 高清”四档超分方案，不保留并行枚举、旧 shader 组合或第二状态源。

本轮目标与实现边界：

- 统一播放器按钮、锚定菜单、日志弹窗、加载/错误卡片和手势提示的深色现代视觉；继续使用现有
  播放器专用图标与唯一 Compose 控制层
- 右上角更多菜单增加可直接切换的“自动旋转”开关，写入现有
  `PlayerSettingsStore.followGravityRotation`，由 `PlayerActivity` 当前方向策略实时消费
- 左半屏纵向手势仍控制亮度，但提示条改到右侧；右半屏纵向手势仍控制音量，但提示条改到左侧
- 倍速菜单从上到下固定为 `3.0x / 2.0x / 1.5x / 1.25x / 0.75x / 0.5x`；内部合法倍速继续保留
  `1.0x`，用于默认速度、恢复正常速度和长按加速跨档
- 播放器设置增加“长按加速”开关。按住画面超过阈值后，当前倍速只临时提升到下一档；
  松手、取消手势、界面销毁或媒体会话变化时恢复按下前速度，临时速度不得写入倍速记忆
- Anime4K 严格对照 `D:\10_Project\mpv-android-anime4k@32f5f169` 的 Balanced/M 质量链，
  顺序固定为“关 / A / B / C / A+ / B+ / C+”，文案分别为“原始画质 / 强力重建 /
  柔和重建 / 降噪处理 / 双重强化 / 双重柔和 / 降噪强化”
- 复制参考仓库所需的原始授权文本与 shader 字节并锁定 SHA-256；模式切换必须校验缓存文件、绝对路径、
  MPV `glsl-shaders` 实际属性和诊断日志，任何失败进入现有可见错误链路
- 逐项复核默认/记忆倍速、连播、双击、按钮跳转、精确 seek、章节、缩略图、解码、GPU、
  Vulkan、音量、字幕、保存目录、自动旋转、后台、全屏退出和网络缓存的唯一 owner 与消费路径

串行实施与验收门禁：

1. [DONE] 审计现有播放器 UI、设置、手势、旋转、倍速和 Anime4K 调用链，并与参考仓库逐项映射
2. [DONE] 重建统一播放器弹窗、按钮与中央/双侧手势反馈视觉
3. [DONE] 接通更多菜单自动旋转、亮度音量提示换侧和指定倍速菜单顺序
4. [DONE] 实现长按加速设置、会话级临时倍速 token、松手恢复和冲突手势处理
5. [DONE] 移植七档 Anime4K Balanced/M shader 链、资产哈希校验和 MPV 属性回读
6. [DONE] 更新播放器单元/静态资源门禁、`CONTEXT.md`、`README.md` 和阶段 11 架构文档
7. [DONE] 执行播放器定向测试、资源门禁、Kotlin 编译、formal readiness、
   `git diff --check` 和串行 Debug APK 构建核验
8. [PENDING] 目标设备验收按钮热区、弹窗锚点、亮度音量换侧、双击/拖动提示、长按恢复、
   重力旋转和七档 Anime4K 画质、性能、温度与 MPV 日志

后续视觉修订：

1. [DONE] 移除 `LegacyImageButton` 和 `LegacyTextButton` 的静态圆形/胶囊底色及描边，
   保留原尺寸点击热区、禁用透明度和受控点击涟漪
2. [DONE] 由 `DropdownMenu` 自身统一绘制 `20dp` 深色圆角表面，删除叠加在默认菜单表面上的
   第二层背景，避免四角露出白色延伸
3. [DONE] 执行播放器静态资源门禁、Kotlin 编译、差异检查与 Debug APK 构建核验

本地验证证据：

- 播放器三组定向 JVM 测试 `38/38` 通过，零失败、零错误、零跳过；Anime4K 资产与静态链路
  Python 门禁 `6/6` 通过
- `:app:compileDebugAndroidTestKotlin`、formal readiness 和 `git diff --check` 通过
- `:app:assembleDebug` 完成 `233` 个任务、零失败，`:app:verifyDebugPlayerRuntimePackaging`
  通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482616169` 字节，
  SHA-256 `1679B8140A9B5715AA8D5B0C5633D12185ABE9E6DDD1791150C108887EEBB907`
- APK 为 `com.kiyori`、版本 `45 / 0.1.0`、min 26、target 34、`arm64-v8a`；Android Debug
  V2 签名和 16 KB ZIP 对齐通过
- APK 内十个 `assets/shaders/*.glsl` 数量、文件名和 SHA-256 均与
  `mpv-android-anime4k@32f5f169` 精确一致；真实设备上的 shader 编译、视觉差异、性能和温度
  仍保持 `verification_pending`
- Crash Report `a0f86ddc-8565-497c-8653-ade1904f44a2` 证明已有开发安装仍保存旧
  `off / fast / balanced / quality` ID；读取链现已在严格枚举解析前一次性迁移为
  `OFF / B / A / A_PLUS`，未知值继续严格拒绝
- 后续视觉修订已通过新增静态断言、`:app:compileDebugKotlin`、formal readiness、
  `git diff --check` 和最终 `:app:assembleDebug`；统一按钮入口不再绘制静态底色/描边，
  `PlayerPopupMenu` 仅使用 `DropdownMenu` 自身的深色圆角表面

## 2026-07-30 文件下载器设置主题边界崩溃修复

Crash Report `92a847df-518a-477d-b3cb-870b17273550` 显示，文件下载器设置打开底部选择面板时，
`KiyoriSettingsSelectionSheet` 读取不到 `LocalKiyoriSettingsColors`。折叠列表内部已经提供
`KiyoriSettingsTheme`，但选择面板与列表是同级节点，原主题边界没有覆盖完整设置子页。

本轮修复门禁：

1. [DONE] 在 Kiyori Shell 子页面宿主为浏览器、下载器和播放器设置提供完整主题边界
2. [DONE] 保留缺少主题时的严格异常，不添加默认颜色或回退逻辑
3. [DONE] 增加 Shell 设置子页主题映射回归测试
4. [DONE] 执行定向 JVM、Kotlin 编译、formal readiness、差异检查和 Debug APK 核验
5. [PENDING] 在报告设备复测文件下载器设置的全部选择面板，并顺带复测播放器设置面板

详细根因、实现与验收记录继续写入
[`kiyori_settings_theme_unification/3_implementation_and_validation.md`](kiyori_settings_theme_unification/3_implementation_and_validation.md)。

本地证据：`KiyoriShellStateTest` 与 `KiyoriSettingsPagesTest` 合计 `56/56`，零失败、零错误、
零跳过；测试任务完成 `:app:compileDebugKotlin`。formal readiness、`git diff --check` 和
`:app:assembleDebug` 通过，构建共 233 个任务、零失败，播放器运行时打包校验通过。Debug APK
为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482615791` 字节，SHA-256
`E3F7D90A72851CBBFB4479A313CD8D0BE50F2B1346D5DD1D4CEBA09835DA721E`；包名
`com.kiyori`、版本 `45 / 0.1.0`、min 26、target 34、`arm64-v8a`，Android Debug V2 签名
与 16 KB ZIP 对齐通过。

## 2026-07-29 全量 UI 与功能逻辑链路审计优化

本阶段以两个目标进行横向封板：覆盖全部用户可达页面、抽屉、弹窗和特殊渲染域的视觉一致性，
并逐条检查现有功能从入口、导航、状态 owner、执行层、持久化到结果反馈的完整链路。只修复现有
功能和真实缺陷，不新增产品能力，不建立第二运行时、第二状态源或任何 fallback。

完整页面矩阵、语义配色、特殊渲染边界、功能链路、实施顺序和验证门禁见
[`kiyori_full_ui_and_function_chain_audit/`](kiyori_full_ui_and_function_chain_audit/index.md)。
本轮持续工作窗口截止到 `2026-07-30 10:00 Asia/Shanghai`；未授权的设备、提交、推送和发布
不在范围内。

当前代码实现、静态反向审查、660 项 JVM 测试、AndroidTest 编译、正式开发准备检查、差异检查、
完整 Lint、Debug APK、播放器运行时打包、Debug 签名、包信息、ABI 和 16 KB ZIP 对齐均已
通过。目标设备上的视觉与真实系统能力验收继续标记为 `verification_pending`。

## 2026-07-29 全局页面视觉一致性与语义彩色图标

本阶段继续收口取消旧全局自定义配色后的剩余影响，建立不依赖用户颜色、只随固定 Kiyori
浅色/深色主题变化的全应用语义色系统。应用壳、正文、卡片和浏览器 chrome 保持中性层级；
蓝、绿、紫、橙、红、青、粉用于图标容器、状态、分组标识和选中边界，不把页面改造成大面积
高饱和彩色界面。

重点覆盖负一屏、AI 模态左抽屉、浏览器菜单及内容抽屉、相关弹窗、AI 对话入口，以及包管理、
权限和工作流页面。设计合同、稳定颜色映射、页面覆盖矩阵、实施顺序和验证门禁见
[`kiyori_global_ui_visual_unification/`](kiyori_global_ui_visual_unification/index.md)。

本地实现与验收已完成：7 个定向 JVM 测试类共 `75/75` 通过，主源码和测试源码编译、
formal readiness、`git diff --check` 与 Debug APK 构建均通过。APK 为 `com.kiyori`、
`45 / 0.1.0`、`arm64-v8a`，Android Debug v2 签名和 16 KB ZIP 对齐通过。未安装 APK、
未操作设备，浅色/深色视觉、抽屉弹窗、工作流画布和触摸热区保持 `verification_pending`。

## 2026-07-29 设置 UI、主题边界与现代化配色统一

本阶段继续整理设置系统：取消用户颜色对 Kiyori 全应用的控制，保留固定浅色、深色和跟随系统
主题；背景、气泡、头像、聊天头部和输入区继续作为 AI 对话局部个性化。设置首页、拆分设置根、
浏览器、下载器、播放器及关键子页使用同一套浅深色 token 和蓝绿紫橙红青粉语义图标色阶。

主题 owner、必须删除的旧状态、设置视觉 token、覆盖页面、实施顺序和验收门禁见
[`kiyori_settings_theme_unification/`](kiyori_settings_theme_unification/index.md)。

本地实施与验收已完成：旧全局颜色路径和资源为零，设置主题覆盖顶栏与正文，定向 JVM 测试
`64/64`、Kotlin 编译、formal readiness、`git diff --check` 和 Debug APK 构建均通过。APK 为
`com.kiyori`、`45 / 0.1.0`、`arm64-v8a`，Android Debug V2 签名与 16 KB ZIP 对齐通过。
未安装 APK、未操作设备，视觉和交互验收保持 `verification_pending`。

## 2026-07-29 设置页信息架构与统一视觉

本轮把原综合 AI 设置按实际 owner 拆分，并以文件下载器设置页为统一视觉标准。Settings Home
继续保持 `4/4/4/4`：第一组固定为“账号与连接 / AI 助手 / 语音服务 / 小程序管理”，删除
“剪贴板口令”和独立“小程序订阅”；“界面定制”和“数据备份与同步”开始承接从 AI 设置移出的
应用级入口，普通网站 Cookie 清理迁入网页浏览器设置。

详细信息架构、状态 owner、返回合同、视觉标准和串行门禁见
[`kiyori_settings_information_architecture/`](kiyori_settings_information_architecture/index.md)。

当前实现和本地验收已完成：设置与 Shell 定向测试 `52/52` 通过，formal readiness、
`git diff --check`、Debug APK 构建、包元数据、v2 签名和 16 KB ZIP 对齐均通过。未安装 APK、
未操作设备，设置页视觉、长语音表单、GitHub 登录弹窗与返回交互保持
`verification_pending`。

## 2026-07-29 文件下载器全链路深度优化与阶段封板

本阶段以现有 `BrowserDownloadManager` 为唯一任务、调度和状态所有者，对浏览器下载、播放器
下载、网页下载确认、Shell/浏览器共享下载抽屉、文件下载器设置、系统下载器桥接、M3U8
离线包、SAF/公开目录交付和 APK 安装清理进行一次封板级审计与闭环。项目尚未发布，因此允许
直接清理未对外形成兼容合同的旧内部方案；不得新建第二下载队列、第二任务数据库、平行状态
owner 或回退逻辑。

审计确认的优先级与失败边界：

- 同名任务必须在进入队列时原子预留最终文件和临时文件，不能让快速连续任务写入同一路径
- 任务快照必须串行、原子落盘，进程终止或并发进度更新不能破坏整个任务历史
- 多个网页下载请求必须排队确认，不能因全局单槽 `check` 触发崩溃或覆盖前一请求
- 断点续传必须使用资源校验器确认服务端表示未变化；不能把不同版本的同名资源拼接到一起
- 错误、日志和用户文案不得泄露带签名参数、Cookie 或授权信息的完整 URL
- 后台下载继续复用唯一 Manager；Android 14 及以上使用用户发起数据传输任务宿主，旧版本使用
  `dataSync` 前台服务宿主，不用第二套 WorkManager 下载数据库
- 下载抽屉和设置页只呈现有真实执行链路的能力；搜索、筛选、批量操作、状态说明、通知与目录
  语义必须一致且可访问
- M3U8、安装包清理和系统下载器能力按真实平台边界实现；不使用计时删除、伪成功或隐式降级

串行实施与验收门禁：

1. [DONE] 数据正确性与安全：
   - 原子化任务状态文件，集中序列化持久化入口
   - 原子预留任务文件名，覆盖队列中尚未创建文件的同名任务
   - 下载确认请求改为 FIFO，逐项确认或取消
   - 传输错误统一脱敏；补充 ETag、Last-Modified 与 `If-Range` 安全续传合同
   - 增加相关单元测试、formal readiness、`git diff --check` 和 Debug APK 构建核验
2. [DONE] 后台运行与通知：
   - 为唯一 Manager 增加平台调度宿主和明确的系统停止/重启状态
   - 建立进行中通知、完成/失败通知、点击进入共享下载抽屉和可解释操作
   - 核验应用重建、进程终止、用户暂停与系统中断不会相互混淆
3. [DONE] 下载抽屉、设置和文案：
   - 增加搜索、状态筛选、全选和批量操作闭环，统一点击热区、空态、错误与进度表达
   - 补充有真实执行链路的网络策略、后台运行与通知设置，删除误导或重复入口
   - 统一资源文案、类型命名和文件命名，删除不再使用的旧 UI/策略代码
4. [DONE-local] 格式能力与最终封板：
   - 完成 M3U8 边界、字节范围、资源规模和离线包完整性审计
   - 把 APK 清理改为基于真实安装结果的持久化闭环，或删除无法兑现的旧选项
   - 同步 `README.md`、`CONTEXT.md` 和下载中心完成度文档
   - 执行风险相称的定向测试、Kotlin 编译、formal readiness、差异/敏感内容审计以及最终
     `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
   - 核验最终 Debug APK 的时间、大小、SHA-256、包信息、签名和 zipalign；设备视觉与真实网络
     验收在未安装 APK 前保持 `verification_pending`

里程碑 1 本地证据：

- `BrowserDownloadPolicyTest 24/24`、`BrowserDownloadTransportTest 18/18`、
  `BrowserDownloadM3u8RuntimeTest 5/5`、`BrowserDownloadDrawerPolicyTest 8/8`，合计
  `55/55`，零失败、零错误、零跳过
- `:app:compileDebugKotlin`、formal readiness 和 `git diff --check` 通过；差异检查只有既有
  CRLF 到 LF 提示，没有 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 15s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 20:32:39 +08:00`，大小 `482591403` 字节，SHA-256
  `475E1F5E1BDF800549F5902918E3231F0E2C0A867636D78F7925258B399A0260`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34；Android Debug v2 签名和
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK、未操作设备；真实断点续传、资源变化、快速同名任务和进程终止仍需在后续设备/
  后台运行里程碑统一验收

里程碑 2 本地证据：

- `BrowserDownloadRuntimePolicyTest 5/5`、`BrowserDownloadPolicyTest 24/24`、
  `BrowserDownloadTransportTest 18/18`、`BrowserDownloadM3u8RuntimeTest 5/5`、
  `BrowserDownloadDrawerPolicyTest 8/8`、`MainActivityBrowserActionTest 1/1`、
  `KiyoriShellStateTest 42/42`，合计 `103/103`，零失败、零错误、零跳过
- `:app:compileDebugKotlin`、formal readiness 和 `git diff --check` 通过；差异检查仍只有
  CRLF 到 LF 提示，没有 whitespace error
- APK 内 Manifest 已由 `aapt dump xmltree` 核验：
  `RUN_USER_INITIATED_JOBS`、受 `BIND_JOB_SERVICE` 保护的 `BrowserDownloadJobService`、
  `dataSync` 类型的 `BrowserDownloadForegroundService`、私有运行时 Action Receiver 和
  Boot Receiver 均存在，导出边界符合设计
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 3m 26s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 21:20:59 +08:00`，大小 `482597303` 字节，SHA-256
  `D81A5C2865A265C0C9E37394401F9A9E32190B05A74BCECD031F35D45F1921A9`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34；Android Debug v2 签名和
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK、未操作设备；Android 14+ UIDT 调度、Android 13- dataSync 通知、网络切换、
  Task Manager 停止、进程重建、重启恢复和通知动作保持 `verification_pending`

里程碑 3 与最终封板本地证据：

- 设置模型升级为 version 2，下载设置固定为 `5/4/2/2/1` 共 14 行；下载抽屉接入搜索、状态
  筛选、可见目标全选、独立批量取消/删除、筛选空态和可访问性说明
- M3U8 离线包增加 4 MiB 文本上限、`#EXTM3U` 校验、master 循环检测、重复/字节范围资源
  去重、256 项有界批次、KEY/MAP/PART 资源完整性校验，以及独立音轨/画面/字幕 playlist 的
  明确拒绝
- APK 自动清理已删除 90 秒计时路径；任务记录持久化包名、版本和安装前版本，SAF 暂存只用于
  包信息解析并立即清理，最终删除严格依赖 `PACKAGE_ADDED` / `PACKAGE_REPLACED` 的精确包名与
  版本匹配。完成通知的“打开”动作也通过 `MainActivity` 回到唯一 Manager
- `BrowserDownloadPolicyTest 26/26`、`BrowserDownloadTransportTest 19/19`、
  `BrowserDownloadM3u8RuntimeTest 7/7`、`BrowserDownloadDrawerPolicyTest 10/10`、
  `BrowserDownloadRuntimePolicyTest 8/8`、`KiyoriSettingsPagesTest 8/8`、
  `MainActivityBrowserActionTest 1/1`、`KiyoriShellStateTest 42/42`，合计 `121/121`，
  零失败、零错误、零跳过
- `:app:compileDebugKotlin`、formal readiness 和 `git diff --check` 通过；敏感内容审计无凭据
  文件或常见密钥模式，唯一 `secret` 命中是既有布尔参数 `secret = false`
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 10s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 22:26:17 +08:00`，大小 `482597303` 字节，SHA-256
  `A1ECBF2DCE6301445ECD544885BDA9F467FD28E8503AED84DD9E6C930B651B18`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、compile 36；Android Debug v2
  签名有效，`zipalign -c -P 16 -v 4` 通过。APK Manifest 已核验 UIDT 权限、JobService、
  dataSync 前台服务、运行时/启动接收器和安装结果接收器
- 本阶段本地代码与制品封板完成；未安装 APK、未操作设备。真实 UIDT/dataSync 生命周期、
  网络切换与漫游、服务端表示变化、SAF 写入、系统安装成功/取消广播、抽屉视觉与触摸仍为
  `verification_pending`

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
   - 左下角“超分”按钮改为锚定弹窗；当前实现已收敛为关、A、B、C、A+、B+、C+ 七档，
     选择后立即应用
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

## 2026-08-03 浏览器页面源码工作台

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器菜单能力的唯一进度载体。Kiyori 尚未发布，当前只读源码抽屉直接替换为浏览器宿主内
全屏原生源码工作台，不保留平行旧页面。实现深度参考
`D:\03_Default\图片\Kiyori\查看源码` 与 `D:\10_Project\hikerView`，但继续以当前唯一
`StandardBrowserSessionTools`、WebSession、WebView 和 `NativeCodeEditor` 为状态与能力边界。

细化步骤：

1. [DONE] 把页面源码状态绑定到捕获时的 session、URL 和 Document token，明确展示当前
   运行中 DOM、页面标题、host、字符数、行数和临时修改语义
2. [DONE] 读取源码时保留 doctype，排除 Kiyori 文本选择浮层等浏览器自有临时节点，并对空文档、
   超大文档、标签切换和导航后的失效状态给出明确结果
3. [DONE] 使用现有全屏 `NativeCodeEditor` 提供 HTML 语法着色、行号、补全、查找替换、
   撤销重做、格式化、复制、重新读取和输入法期间的单层符号栏
4. [DONE] 增加统一源码差异预览与显式应用确认；应用只写入捕获时的同一活动 Document，
   使用 `document.open/write/close` 重建当前文档，不创建新 WebView、session、历史项或持久网页副本
5. [DONE] 离开未应用编辑时提供继续编辑、保留编辑返回网页和丢弃三种明确选择；重新读取会先确认
   是否覆盖当前编辑，系统 Back、顶栏返回和浏览器 Back 共用同一状态机
6. [DONE] 增加 `browser_page_source` AI 工具，显式区分读取实时 DOM 与读取当前编辑草稿；
   大结果必须指定输出文件，AI 修改网页继续作用于同一个 Browser Runtime
7. [DONE] 更新 `CONTEXT.md`、README、浏览器菜单能力文档、AI 工具类型文档与中英文工具描述；
   不新增网络源码抓取器、第二编辑器、第二 Browser Runtime 或持久化网页副本
8. [DONE] 增加源码捕获、Document 校验、HTML 应用脚本、大小边界、差异摘要和 Back 路由
   定向测试，并以编译与静态检查核对 AI 工具接线；再执行 formal readiness、资源解析、差异检查和
   Debug APK 构建核验
9. [DONE] 修复压缩 HTML、内联脚本和超长属性产生的极宽横向画布：新增不改源码字符的视觉软换行，
   页面源码默认开启并可切换横向浏览；统一续行绘制、触摸、光标、选区、上下移动、补全锚点和滚动
   映射。顶栏精简为返回/标题/搜索/更多，紧凑工具条增加真实撤销重做状态、格式化、复制、跳转行、
   行列/选区和超长行统计，格式化与替换进入同一可撤销历史。进入横向浏览时强制从源码左上角开始，
   不再由超长行末尾的旧光标触发自动右移或下移
10. [PENDING] 目标设备验收状态栏、窄屏顶栏、输入法、长源码滚动、查找替换、应用后页面行为、
   刷新恢复、跨标签保护和 AI 读取；本轮不安装或操作设备

本轮本地证据：

- `BrowserPageSourceSupportTest` 11 项、`EditorVisualLayoutTest` 9 项与
  `WebSessionBrowserBackPolicyTest` 2 项合计 `22/22`，覆盖源码安全编码、捕获/应用结果、
  Document token、大小边界、普通/超大差异、长行统计、行跳转、字符簇软换行、十万字符连续布局和
  横向浏览进入时的左上角复位、返回软换行时的纵向上下文保留与 Back 顺序
- 完整 `:app:testDebugUnitTest` 为 141 个测试套件、`853/853`，零失败、零错误、零跳过；
  `:app:compileDebugKotlin` 与正式开发准备检查通过
- 七份实际 `strings.xml` 均可解析，44 个 `web_session_source_*` 键及占位符集合一致；
  旧只读源码 UI 和旧抽屉分支引用均为 0，AI 工具在注册、双语提示、执行器、JavaScript 包装和
  工具类型映射中接线完整，`git diff --check` 通过
- ARCH041 语义色消费者快照已登记源码工作台，两项对应架构契约测试通过。完整架构门禁仍报告
  当前 HEAD 已存在的 ARCH046 漂移：`UserscriptSourceExportHelper.kt` 已直接使用 `KiyoriPaths`，
  但 M-05E 固定消费者快照尚未登记；本轮未改动该既有 userscript 导出实现
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 1m 52s`，238 个任务、29 个执行、209 个 up-to-date，
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，宿主文件时间
  `2026-08-04 03:43:10 +08:00`，大小 `471581414` 字节，SHA-256
  `F2D7683FE6EE114B7171F635B58A0FB24EECF32DB1B0E445C6C2D4B5547FA97E`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、compile 37、仅 `arm64-v8a`；
  51 个 native 库无重复 basename，Android Debug V2 签名与
  `zipalign -c -P 16 -v 4` 均通过

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
