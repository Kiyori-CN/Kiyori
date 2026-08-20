---
status: verification_pending
baseline: e42bd44f
---

# 模态 AI 左抽屉与设置归属

## 当前任务

删除首个 App Shell 切片中的全屏 AI Center，恢复由三横线按钮触发、覆盖当前页面的模态 AI 左抽屉。Kiyori 从未发布，因此本任务不保留全屏页面、旧状态、字符串、路由来源或双入口。

## 已确认入口

| 分组 | 入口 | 目标与 owner |
| --- | --- | --- |
| 高频状态 | 包管理 | 现有 Packages 一级页面 |
| 高频状态 | 工具箱 | 现有 Toolbox 一级页面；徽标统计唯一 `AppNavigationModel` 中的 `NavigationSurface.TOOLBOX` 条目 |
| 高频状态 | 工作流 | 现有 Workflow 一级页面 |
| AI 功能 | AI 对话 | 返回现有 AI Home，不创建会话、不清空草稿 |
| AI 功能 | 助手配置、记忆库 | 现有对应一级页面 |
| 插件 | ToolPkg 动态入口 | 保持 route ID、注册协议和注册顺序；动作入口只执行一次 |
| 固定入口 | AI 设置 | 现有 Settings 页面和持久状态 |

帮助、关于、使用手册和 Terminal 不进入抽屉。Terminal 继续只位于 AI 首页右上角；工具箱不迁入小程序首页。权限授予不再显示在抽屉；“设置 - 更多功能 - 权限”通过当前 settings session 进入 `KiyoriSettingsRoute.PERMISSIONS`，与首次启动共享设备权限事实和动作，不新建第二份授权状态。
文件管理器和用户协议也不再作为 Toolbox host entry 投影。文件管理首页“手机存储”与设置首页
“文件管理器”共同进入唯一 `KiyoriShellChild.FILE_MANAGER`，协议入口迁入“设置 - 更多功能 -
用户协议与隐私政策”并复用现行只读法律文档。

## 模态容器合同

- 只能点击 AI Home 或 AI 一级页面左上角三横线打开
- 禁止左边缘打开、横向拖动和滑动关闭
- 系统 Back 优先关闭抽屉，点击右侧遮罩关闭抽屉
- 抽屉左右滑入或滑出，遮罩同步淡入或淡出
- 底层页面保持原坐标、尺寸、正面朝向、圆角和不透明度；继续渲染但不接收触摸
- `<600dp` 使用窗口宽度 `75%`，`600-839dp` 使用 `320dp`，`>=840dp` 使用 `360dp`
- 分隔铰链限制抽屉不越过左侧物理区域，不恢复 `64dp/280dp` 永久侧栏
- 遮罩覆盖整个窗口；抽屉面板最高点位于状态栏底部，不覆盖状态栏区域
- 面板内容只处理水平与底部安全区，不重复加入状态栏顶部 inset
- 左侧两角为直角，右上与右下为 `16dp` 圆角
- 遮罩使用主题暗色约 `32%` 不透明度，并保留抽屉边缘阴影
- 使用共享 `OperitTheme` 的组件视觉语言，默认色板遵守 [专业浏览器灰白默认主题](../../doc-src/decisions/0007_professional_browser_theme.md)，不恢复抽屉专属玻璃、背景色、强调色或持久化偏好

## 导航与状态合同

- AI Home、包管理、工具箱、工作流、助手配置、记忆库、ToolPkg 一级页面和从抽屉进入的 AI 设置显示三横线；快捷方式、Widget 或外部路由直达这些一级根页面时规则相同
- 上述页面的深层子页面显示返回箭头
- 每个一级根路由显式保存注册入口 ID。宿主入口只按 route ID 匹配，ToolPkg 插件入口按 route ID 与注册参数共同匹配；禁止从 route args、返回栈深度或 instance ID 前缀推断根归属
- 浏览器、小程序、文件管理和 Kiyori 设置不显示 AI 抽屉按钮
- 点击其他入口先同步建立目标一级路由，再关闭抽屉；一级页面互相替换，不连续压栈
- 点击当前入口只关闭抽屉，不创建新路由实例
- AI 一级页面各自保留滚动、筛选、表单和子页面栈
- 原生 AI 一级根使用稳定宿主实例并保留状态；ToolPkg 根每次进入创建新路由实例，只有其 `RouteSpec.keepAlive=true` 时才保留组合状态和已保存子栈
- `AppContent` 禁止 crossfade 的直接替换会把策略保持到下一次真实 route 变化，并绕过 alpha
  tween：当前屏幕直接绘制为不透明，所有保活非当前屏幕直接绘制为透明；AI Home 与内部对话
  历史抽屉继续保留组合状态，但不能在 Settings 权限页首帧参与绘制
- AI 一级页面 Back 返回 AI Home；深层页面 Back 返回所属一级页面
- AI 设置从抽屉进入时作为 AI 一级页面，Back 返回 AI Home；从 Kiyori 设置首页进入时显示返回语义并返回设置首页
- AI 设置只有一份页面、表单与持久状态；同来源族可恢复自己的子页面栈，跨来源族始终从 AI 设置根页开始

## 系统栏合同

- Kiyori Shell 对软件首页、负一屏、底部五入口根页面和 AI 页面统一使用 edge-to-edge 状态栏
- 页面背景与抽屉全屏遮罩绘制到手机物理顶边；抽屉面板是明确例外，从状态栏底部开始
- 全屏文件管理 child 与法律文档设置页自行绘制不透明全尺寸背景，并让内容消费
  `WindowInsets.safeDrawing`；页面背景保持 edge-to-edge，工具栏和正文不得进入系统栏
- 状态栏显示时保持透明，并在系统支持时禁用对比度遮罩；图标明暗由当前前景表面决定
- 删除继承的“透明状态栏”“自定义状态栏颜色”设置及持久化，只保留“隐藏状态栏”

## AI 生命周期合同

AI Home 保持单一稳定宿主。打开或关闭抽屉、切换 AI 一级页面时，不暂停、取消、销毁、重建或在 Pager、子组合和不同宿主之间搬移 AI Composable。无模型配置或 API Key 时也必须显示对话界面和输入框；模型配置通过独立设置页面完成，缺少凭据不得替换或阻塞 AI Home。流式回答、思考、工具调用、附件、草稿、当前会话和滚动位置持续存在。

## 实现步骤

1. [已完成] 更新当前文档合同、新 ADR 和工作区 checkpoint
2. [已完成] 删除全屏页面、状态、来源枚举和七套 AI Center 字符串
3. [已完成] 建立无手势模态容器、响应式宽度和分隔铰链约束
4. [已完成] 从 `ScreenRouteRegistry` 与 ToolPkg 目录生成抽屉入口，恢复原版状态头部、快捷卡、列表、图标和插件顺序
5. [已完成] 建立一级页面替换、独立状态栈、当前入口关闭、AI 设置返回 AI Home 和动作入口单次执行
6. [已完成] 保持 AI Home 持续组合并补充纯状态测试
7. [已完成] 顺序执行授权的 Debug 门禁、测试、lint 和构建，记录新 APK 元数据
8. [已完成] 保留真机 `verification_pending` 清单
9. [已完成] 为 AI 一级根写入显式入口 ID，统一四类外部导航匹配并建立菜单/返回模式解析
10. [已完成] AI 设置使用抽屉与 Kiyori 设置两类明确来源；同来源族保留子栈，跨来源族打开根页
11. [已完成] 统一 Kiyori Shell 透明状态栏并删除冲突的旧设置、偏好和字符串
12. [已完成] 重新执行授权的 Debug 验证序列并更新 APK 证据；真机项目继续见下方 `verification_pending`
13. [已完成] 首页三页共享 Pager 输入与 fling，删除 AI 覆盖层阈值跳转
14. [已完成] 抽屉面板移到状态栏下方，保留全屏遮罩、动态网络状态与原动画参数
15. [已完成] ToolPkg 一级根服从自身 `keepAlive`，并清理运行时已移除插件的保存栈
16. [已完成] 将高频入口调整为“扩展 / 工具箱 / 工作流”，删除抽屉权限状态查询和短标签资源；工具箱徽标直接统计唯一导航模型中的宿主与 ToolPkg 工具条目
17. [已完成] 将原权限入口接入“设置 - 更多功能 - 权限”；初版复用
    `Screen.ShizukuCommands` 和 `RouteEntrySource.KIYORI_SETTINGS` 返回链
18. [已完成] 从 Toolbox 导航目录删除文件管理器与协议 host entry；将手机存储和设置首页
    文件管理器接入同一个 Shell child，将只读法律文档接入 More Features 的 Settings route
19. [本地完成，待真机复测] 修复法律文档/File Manager 的不透明安全区页面根，并修正无
    crossfade 时保活 AI 屏幕错误变为不透明及退场插值重新显现的合成规则
20. [本地实现与自动验证完成，待真机复测] 将权限入口改为 Settings 自有 `PERMISSIONS` route，复用首次启动 21 项权限
    事实和动作；顶栏 actions/title 按缓存 screen key 隔离，权限首屏移除 Demo/Terminal/MCP 加载链

## 自动验收

- `KiyoriShellStateTest` 覆盖抽屉 Back 优先级、响应式宽度、一级页面替换、当前入口关闭和 AI 设置返回 AI Home
- 源码不存在全屏 AI Center 页面、路由来源、状态或当前字符串
- 源码不存在 `PhoneLayout`、`TabletLayout`、全局 `draggable`、`drawerProgress` 或页面透视变换的重新接入
- ToolPkg 动态入口 route ID、注册协议与顺序保持不变
- 原生一级根固定宿主实例；ToolPkg 根每次进入生成新实例，只有 `keepAlive=true` 恢复组合状态和子栈
- 启动、快捷方式、raw route 与 `AppRouterGateway` 对同一宿主根路由使用一致的显式匹配；插件仍要求参数完全匹配
- 自动状态测试覆盖 AI 根菜单、深层返回、AI 一级栈恢复和 `RouteEntrySource.KIYORI_SETTINGS` 的返回语义
- 源码不存在 `statusBarTransparent`、`useCustomStatusBarColor` 或 `customStatusBarColor` 的设置与持久化
- 无模型配置或 API Key 时 AI Home 仍显示聊天内容区和输入框；源码不存在强制替换 AI Home 的配置整页、`shouldShowConfigDialog` 或 `CHAT_ONBOARDING` 路由
- 普通模型与参数配置页的 Back 不经过 API Key readiness guard，缺少凭据不会阻塞离开页面
- 抽屉快捷行只包含 `main.packages / main.toolbox / main.workflow`；`main.shizuku_commands` 不再属于可见抽屉 surface，工具箱徽标数量覆盖宿主与 ToolPkg 的 `TOOLBOX` 条目
- 设置首页最后一项进入 `KiyoriSettingsRoute.MORE_FEATURES`；权限项压入
  `KiyoriSettingsRoute.PERMISSIONS`，Back 恢复同一 More Features route
- Toolbox 导航目录不存在 `toolbox.file_manager` 与 `toolbox.agreement`；ToolPkg 动态条目不变
- 文件管理首页手机存储和设置首页文件管理器复用唯一 `FILE_MANAGER` child；Settings 来源关闭
  child 后恢复原 settings route，child 前景期间 Settings surface 不组合也不抢占 Back
- More Features 的法律文档项进入 `KiyoriSettingsRoute.AGREEMENT`，只读复用现行协议版本和
  `KiyoriLegalDocumentsScreen`；页面根保持不透明并消费 `safeDrawing`，Back 逐级返回
  More Features 与 Settings Home
- File Manager child 的页面根保持不透明并消费 `safeDrawing`；手机存储与设置入口继续复用
  同一 ViewModel、页面 Back 与目录向上语义
- Settings-owned 权限 route 禁止 crossfade 时，策略不会在 route key 稳定后提前恢复；当前屏幕
  最终绘制透明度直接为 `1f`，所有保活非当前屏幕直接为 `0f`，不经过 tween；测试覆盖 AI
  内容层。顶栏 actions/title 另按缓存 screen key 独立登记，只读取当前 key，旧 AI 四个 actions
  不能投影到新 route
  抽屉保持打开状态的旧屏幕不能出现在目标页首帧
- 本次入口收口通过定向 JVM、architecture、formal readiness、Markdown、差异检查与最终
  Debug APK 验证；未运行与本任务无关的额外 lint、Release 或设备检查

## 自动验证记录

- 2026-07-23 当前工作树的 `git diff --check`、正式开发准备门禁和 CI Python 测试 `47/47` 通过
- 定向 `KiyoriShellStateTest` 为 `23/23`，完整 Debug JVM 测试为 `394/394`；Debug Kotlin 编译通过
- 本轮强制 Kotlin 复采通过，告警从 453 降至 436；`app` 从 428 降至 411，`terminal` 保持 25，本轮点名的空值、生命周期、注解 target、系统栏和 Compose 废弃 API 告警均已消失
- `:app:lintDebug --rerun-tasks` 在 `13m 05s` 后通过，报告从 59 条 warning 降至 52 条，2 条 hint 不变；项目资源与 Compose 私有资源同名产生的 7 条 `PrivateResource` 已归零，lint baseline 未修改
- CMake arm64-v8a 重新配置通过；仓库源码没有新增 CMake 告警，保留的 3 条 `CMP0063` 来自 FetchContent OpenFST，2 次 `CXX5304` 来自本机 SDK XML 与旧解析器不匹配
- 当前工作树的 `assembleDebug` 通过；`app/build/outputs/apk/debug/app-debug.apk` 生成于 `2026-07-23 03:31:37 +08:00`，大小 `416308263` 字节，包名 `com.kiyori`，版本 `45 / 0.1.0`，SHA-256 `38F1F9A37CD55902428E1E6D42989DCCCE0C232C0D88E72764B3603AFED1C494`

## 2026-08-19 本轮快捷入口、更多功能与角色选择维护证据

- `CharacterSelectorVisualContractTest` `4/4`、`KiyoriSettingsPagesTest` `14/14`、
  `KiyoriShellStateTest` `71/71`，零失败、零错误、零跳过；任务包含
  `:app:compileDebugKotlin`
- architecture `PASS (phase=m03)`、architecture 单元测试 `109/109`、
  `python -B ci/script/check_formal_readiness.py --repository . --require-main` 和
  `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 通过；`232` 个任务中
  `23 executed / 209 up-to-date`，`verifySingleDebugLauncher` 与
  `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间
  `2026-08-19 16:00:14 +08:00`，`472553854` bytes，SHA-256
  `8C08C8D7150BBDB56EE1018BFE1C24FDB07C70F453A3F0AB9ABA74DF267A87E1`；
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，唯一 launcher、arm64-only、Android
  Debug V2 单 signer 与 16 KB ZIP 对齐通过
- 未安装 APK、未操作设备；抽屉三卡、默认角色头像、排序菜单四角与描边、更多功能权限返回链、
  浅深主题和窄屏布局保持 `verification_pending`

## 2026-08-20 协议入口与文件管理首页收口证据

- Toolbox 导航目录中的 `toolbox.file_manager` 与 `toolbox.agreement` 已删除；动态 ToolPkg
  Toolbox 条目仍由唯一 `ScreenRouteRegistry` 与 `AppNavigationModel` 投影
- “设置 → 更多功能 → 用户协议与隐私政策”复用现行
  `KiyoriLegalDocumentsScreen`、协议版本与正文 owner；文件管理首页“手机存储”和设置首页
  “文件管理器”复用唯一 `FILE_MANAGER` child 与原 `FileManagerViewModel`
- `CharacterSelectorVisualContractTest` `4/4`、`KiyoriSettingsPagesTest` `14/14`、
  `KiyoriShellStateTest` `74/74`、`KiyoriDesignThemeTest` `13/13`，合计 `105/105`
- architecture `PASS (phase=m03)`、architecture 单元测试 `109/109`、formal readiness、
  315 份工作树 Markdown 本地链接与 `git diff --check` 通过
- 最终 `assembleDebug` 在 `1m 48s` 内通过，`232` 个任务中
  `22 executed / 210 up-to-date`；`verifySingleDebugLauncher` 与
  `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间
  `2026-08-21 01:20:27 +08:00`，`472649202` bytes，SHA-256
  `CCBBE04F74981189BF47BFEEBA158E4411BC983972D1E072BC088487D604E237`；
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，唯一 launcher、仅 `arm64-v8a`、
  Android Debug V2 单 signer 与 16 KB ZIP 对齐通过
- 本轮未安装 APK、未操作设备；法律文档、文件管理器、真实文件操作与返回链继续保持
  `verification_pending`

## 真机验收

以下项目在真实 Android 设备验证前保持 `verification_pending`：遮罩与触摸阻断、左右圆角和顶部接缝、滑入滑出帧表现、Back 优先级、旋转、分隔折叠屏边界、以及流式回答期间开关抽屉和切换一级页面的会话持续性。
