---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
baseline: c036a03e
date: 2026-07-29
---

# 全局页面视觉一致性与语义彩色图标

## 2026-09-11 抽屉顶部状态栏白条修复

- 目标：消除独立抽屉窗口顶部未被遮罩覆盖的亮色条；保留状态栏、内容安全区、拖拽与关闭行为。
- 范围：共享 `KiyoriModalBottomDrawer`、浏览器/文件工具箱及文件操作菜单的 Dialog 窗口配置。
  环境变量抽屉和浏览器通用模态窗口已启用边到边绘制；不修改应用根窗口或侧边导航布局。
- 阶段：检查所有共享抽屉宿主及现有差异，补齐 `decorFitsSystemWindows = false`，审阅补丁后串行构建 Debug APK。
- 基线：`main/934ee7978`，保留开始时所有未提交改动。回滚仅撤回本节对应的窗口配置补丁；不提交推送，不操作设备。
- 风险与验收：窗口可绘制范围扩大后，检查半展开/全展开、键盘和导航栏安全区；编译与 APK 核验完成后，
  浅深主题下的顶部遮罩、开合和系统返回仍需目标手机复测，状态为 `verification_pending`。
- 本地验证（2026-09-11）：`git diff --check` 通过，文档检查 516 文件、0 问题；
  `:app:assembleDebug --no-daemon --console=plain` 成功（1m49s，238 tasks）。单 launcher、
  脚本代理及播放器打包、APK V2 单签名与 16 KB ZIP 对齐通过。未额外运行单元测试、Lint 或 Release。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间 2026-09-11 03:54:50（Asia/Shanghai），
  488231589 字节，`com.kiyori / 45 / 0.1.0 / arm64-v8a`，SHA-256
  `56FB34AACC91A102CFD1AAADE95560A6A16A4CEB8C412A7D6DFE37F85EE6819A`。

## 2026-09-11 克制 Material 3 材质统一

- 状态：实现、定向验证与最终 Debug APK 核验通过；真机 `verification_pending`。用户选择“实色表面、轻层次、少阴影”。
  保留上一轮图标和累计改动，基线 `main/934ee7978`。
- 范围：共用表面角色、菜单/弹窗阴影、模态遮罩、输入弹窗不透明性，以及设置/市场/工作流的部分卡片。
- 分阶段执行：① 审查主题、弹窗宿主与消费者；② 基于原 ColorScheme 增加派生材质 token 并接线；
  ③ 验证浅深主题可读性、抽屉接线、架构与 Debug APK。回滚仅针对本轮材料规则和对应接线差异。
- 发现：AI 输入弹窗使用 0.95 或继承输入区的透明度；浏览器自绘 Dialog 与共享模态抽屉未显式消除系统 dim；
  菜单/对话框阴影在 4–10dp 之间散落，部分文件菜单仍继承设置色域。
- 实施：不透明 popup/sheet/card 取自既有 Material 表面色；菜单使用 3dp 轻阴影，模态遮罩统一 0.32；
  自绘遮罩的 Dialog 关闭系统 dim，文件菜单局部使用浏览器中性主题；操作弹窗与个人聊天背景分离。
- 详细规则见[Material 3 材质规范](../../doc-src/architecture/kiyori_material_surfaces.md)。既定顶底栏、双栏密度、
  三行工具箱高度、返回链、点击/长按和数据所有权不变；本轮不提交推送，不操作设备。
- 反向审阅：普通设置/市场卡片接入 card 低层实色，无额外阴影；工作流悬浮标签使用 popup 实色与轻阴影。
  附件弹窗删除已失效的外部颜色参数，避免未来误把输入背景透明度传入菜单。
- 本地验证（2026-09-11）：`KiyoriSurfaceTokensTest` 2 项、`KiyoriDesignThemeTest` 13 项、
  `FileManagerSourceContractTest` 6 项、`KiyoriToolboxDrawerTest` 2 项，共 23 项通过，零失败/错误/跳过；
  精确主题消费者正反例 2 项通过，完整架构 `phase=m03, errors=[]`，正式准备检查通过。
- 最终 `:app:assembleDebug --no-daemon --console=plain` 成功（2m32s，238 tasks）；APK 时间为
  2026-09-11 01:26:37（Asia/Shanghai），大小 488231589 字节，SHA-256
  `F2E0FC7C43F18A46E55B343E97FBA3E639FCF9FB4FEC8C5BF2EBA2DABA6B59B1`。
  包名 `com.kiyori`，版本 `45 / 0.1.0`；单 launcher、代理与播放器打包、V2 单签名及 16 KB ZIP 对齐通过。
  文档检查 515 文件、0 问题；`git diff --check` 通过。累计工作区修改保留，未提交推送。
- 待设备验收：文字/图片聊天背景下的弹窗可读性、深色层级、遮罩开合、小屏/大字体和系统返回。
  未运行 Lint、Release 构建、安装或设备操作；本地验证不替代上述现场验收。

## 2026-09-11 全软件图标用途规范

- 状态：实现与定向验证通过，真机 `verification_pending`；基线 `main/934ee7978`，保留之前累计修改。本轮仅本地实现与验证，不提交推送或操作设备。
- 目标：全局按用途选择轮廓/填充及颜色；软件首页搜索框三按钮、AI 顶栏四按钮、输入区及附件/设置弹窗优先轮廓。
- 阶段：① 扫描与按角色分类；② 统一通用操作及指定界面，修复不随主题的图标色；③ 编译、动态图标与配色测试、架构检查及最终 APK。
- 扫描覆盖产品 Shell 与原生 UI 的 632 个 Kotlin 文件，258 个涉及图标。统一 134 文件中的 446 处通用操作字形，
  保留收藏/书签选中、播放暂停、错误成功、文件类型、头像及品牌图形；设置入口矩阵统一轮廓。
- 唯一规则见[图标用途与配色规范](../../doc-src/architecture/kiyori_iconography.md)。继续复用既有语义色、设置色板和浏览器菜单身份色。
- 风险与回滚：不改变回调、尺寸、排列和持久状态；每项操作符号替换均可独立撤回。动态图标缓存按样式隔离，
  其余页面原默认解析不变；真机大小/深浅主题和状态识别仍需验收。
- 差异复核：上述 134 文件中，130 文件在忽略 import、空白和图标 family 后与基线代码一致；其余为已保留的
  文件菜单、文件顶底栏、悬浮球改动及本轮下载弹窗配色。指定输入组件另行检查，无填充/圆角 family 图标残留。
- 配色与语义修复：下载弹窗的浅蓝硬编码改为随主题变化的下载/网络语义色；“编辑完整链接”使用编辑图标，
  “提取文件后缀”使用自动处理图标。其他已有状态色、菜单逐项身份色及媒体白色覆盖层继续保留。
- 本地验证（2026-09-11）：`MaterialIconNameResolverTest` 2 项、`KiyoriDesignThemeTest` 13 项、
  `WebSessionBrowserMenuColorPolicyTest` 5 项全部通过，0 失败/错误/跳过；完整架构 `phase=m03, errors=[]`、
  正式准备、差异与 514 文件文档检查通过。未执行 Lint、Release、安装或设备验收。
- 最终 `:app:assembleDebug --no-daemon --console=plain` 成功（2m09s，238 tasks）；APK 时间为
  2026-09-11 01:03:32（Asia/Shanghai），大小 485518148 字节，SHA-256
  `0C8ED14A2D80E63A5AC8FE0CD23E057EC2CD1E9B83E0AF987AC813988C15835C`。
  包名 `com.kiyori`，版本 `45 / 0.1.0`，单 launcher、代理与播放器打包、V2 单签名及 16 KB ZIP 对齐通过。
  下一验收：浅深色、小屏/大字体、输入菜单各状态、导航和收藏填充，以及播放器覆盖层的真机可辨识度。

## 目标

在固定 Kiyori 浅色、深色与跟随系统主题已经成为唯一应用主题方案后，继续清理页面内部残留的
浅色硬编码、单一蓝色图标和黑白灰工具矩阵。建立一套全应用可复用的语义色令牌，让颜色稳定
表达功能和状态，而不是被用户全局配色或角色外观改变。

本阶段重点覆盖：

- 负一屏的数据卡片和快捷工具
- AI 对话页的模态左抽屉、快捷入口、包管理、工具箱和工作流入口
- 浏览器菜单抽屉、书签、历史、下载、用户脚本、媒体候选和网络日志等内容抽屉及其弹窗
- 包管理、权限引导和工作流列表/画布的关键视觉层级
- 与上述页面共享的图标、空态、状态和选中组件

## 唯一视觉状态所有者

| 视觉域 | 唯一所有者 | 可变化内容 | 禁止 |
| --- | --- | --- | --- |
| 应用浅深主题 | `UserPreferencesManager.themeMode` 与 `useSystemTheme` | 固定浅色、固定深色、跟随系统 | 用户自定义全局 primary、secondary 或 AppBar 颜色 |
| 全应用语义色 | `KiyoriSemanticTone` 与固定浅深色表 | 图标、低饱和容器、状态和边框随浅深主题变化 | 读取角色卡、聊天气泡、旧全局颜色或网页颜色 |
| 浏览器保护色域 | `KiyoriBrowserTheme` | 中性 chrome 与中性内容表面 | 用大面积应用强调色覆盖地址栏、网页或抽屉背景 |
| AI 对话局部外观 | 现有 AI 外观 preference | AI 背景、气泡、头像、聊天头部和输入区 | 改变抽屉、包管理、权限、工作流或浏览器 |

## 设计文档

- [语义色与组件规则](1_semantic_visual_system.md)
- [覆盖矩阵、实施顺序与验收](2_coverage_and_validation.md)

## 串行里程碑

1. [DONE] 提升设置语义图标色为全应用 `KiyoriSemanticTone`，删除设置专用旧命名
2. [DONE] 统一负一屏和 AI 模态左抽屉
3. [DONE] 统一浏览器菜单、内容抽屉和相关弹窗
4. [DONE] 统一包管理、权限和工作流关键页面，并清理工作流画布浅色硬编码
5. [DONE] 审计 AI 对话页剩余颜色影响，更新测试和正式文档
6. [DONE] 执行定向测试、Kotlin 编译、formal readiness、差异检查和 Debug APK 核验
7. [PENDING] 在目标设备验收浅色、深色、抽屉、弹窗和触摸热区

## 2026-07-30 小范围视觉修订

- [DONE] AI 抽屉三张快捷卡为右上角计数/状态徽标保留独立顶部区域，避免文字覆盖图标
- [DONE] 软件首页天气图标按天气与异常状态使用独立琥珀/金黄、蓝、青、紫、红、橙
- [DONE] AI 顶栏浏览器、终端、工作区动作固定使用蓝、青、紫语义色
- [DONE] App Shell 底部五入口的选中态改为精确 `#FFC153` 填充和页面背景色内部细节，不再使用蓝色空心描边
- [DONE] 按五个 Vector 的可见边界补偿最终尺寸；设置入口使用独立内部圆环细节层，清除外缘残线
- [DONE] 每次点击含重复点击均从较小填充态连续压缩并以低阻尼弹簧放大到原图标视觉尺寸；前四项使用
  `0.42` 阻尼扩大黄色峰值，最右侧设置入口保持 `0.55`
- [DONE] 定向测试 `58/58`、资源/Kotlin 编译、formal readiness、差异检查和 Debug APK 核验通过
- [PENDING] 目标设备浅深色、窄屏徽标间距、五入口最终尺寸和弹性动效验收

本轮 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482617522` 字节，
SHA-256 `22C6A2D7A20D28038DB8B549826F56CA7871F18E79D6748F7D4310FC358AC15A`；包名
`com.kiyori`、版本 `45 / 0.1.0`，Android Debug V2 签名和 16 KB ZIP 对齐通过。

## 2026-08-19 AI 角色选择与左抽屉快捷入口调整

- [DONE] AI 抽屉高频入口调整为“扩展 / 工具箱 / 工作流”，工具箱徽标统计唯一导航目录中的宿主与 ToolPkg 工具；权限入口退出抽屉
- [DONE] 角色选择弹层的角色卡与群组改为中性/蓝色，顶栏默认头像同步改为蓝色；排序菜单为
  `12dp` 圆角、零 tonal/shadow elevation 和 `0.5dp outlineVariant` 描边
- [DONE] `CharacterSelectorVisualContractTest` `4/4`、`KiyoriSettingsPagesTest` `14/14`、
  `KiyoriShellStateTest` `71/71`，零失败、零错误、零跳过；任务包含
  `:app:compileDebugKotlin`
- [DONE] architecture `PASS (phase=m03)`、architecture 单元测试 `109/109`、
  formal readiness、`git diff --check` 和规定 Debug APK 构建通过
- [DONE] 最终 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，
  `472553854` bytes，SHA-256
  `8C08C8D7150BBDB56EE1018BFE1C24FDB07C70F453A3F0AB9ABA74DF267A87E1`；
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，唯一 launcher、arm64-only、Android
  Debug V2 单 signer 与 16 KB ZIP 对齐通过
- [PENDING] 未安装 APK、未操作设备；浅深主题、排序弹窗四角与描边、设置返回链、窄屏三卡布局
  和动态 ToolPkg 数量仍需目标设备验收

## 本地实施结果

- [DONE] 新增 `KiyoriSemanticTheme.kt` 和统一 `KiyoriSemanticIconBadge`；旧
  `KiyoriSettingsIconTone`、旧解析器和旧全局颜色消费者在主源码与测试源码为零
- [DONE] 负一屏、AI 模态左抽屉、浏览器工具菜单、书签、历史、下载、用户脚本、媒体候选、
  网络日志及相关弹窗使用稳定语义色；浏览器 chrome 和网页仍保持中性
- [DONE] 包管理标签与列表、权限引导/权限级别、Shizuku 设置向导、工作流列表/模板/状态、
  工作流画布与节点卡完成浅深主题统一
- [DONE] AI 对话默认历史、悬浮、Token 状态和角色选择弹层获得语义色与深色表面；AI 背景、
  气泡、头像、聊天头部和输入区继续由原局部外观 owner 持有
- [DONE] `KiyoriThemeTest 9/9`、`KiyoriSettingsPagesTest 11/11`、
  `KiyoriShellStateTest 44/44`、`PackageManagerVisualPolicyTest 1/1`、
  `WebSessionBrowserUserAgentRoutingTest 1/1`、`WebSessionBrowserChromeLayoutTest 7/7`、
  `WebThemeSnapshotSchemaTest 2/2`，合计 `75/75`，零失败、零错误、零跳过
- [DONE] `:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin`、
  formal readiness 与 `git diff --check` 通过
- [DONE] `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 31s`，233 个任务零失败，`:app:verifyDebugPlayerRuntimePackaging`
  通过
- [DONE] Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小
  `474822245` 字节，SHA-256
  `10D24B77EAA2D4776E130DBB603D7352221F56BC667605C2537A6B648D49BB71`
- [DONE] APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、compile 36、
  `arm64-v8a`；Android Debug v2 签名和 `zipalign -c -P 16 -v 4` 通过
- [NOTE] 构建机文件系统时间显示 `2026-07-30 03:59:23 +08:00`，晚于当前日期
  `2026-07-29`，属于本机时钟偏差，不作为项目日期
- [ ] 未安装 APK、未操作设备；浅色、深色、系统切换、抽屉高度、弹窗、触摸热区和工作流
  Canvas 真机视觉保持 `verification_pending`

## 非目标

- 不改变 NavigationEntry、Screen、WebSession、PackageManager、权限或 Workflow 的状态所有者
- 不新增动态取色、壁纸取色或用户自定义全局配色
- 不改变浏览器地址栏、网页内容和媒体播放状态机
- 不借本轮重构项目目录、升级依赖或创建平行 UI/数据实现
- 不提交、不推送、不安装 APK、不操作设备
