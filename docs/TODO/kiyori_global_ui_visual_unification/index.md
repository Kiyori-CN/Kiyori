---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
baseline: c036a03e
date: 2026-07-29
---

# 全局页面视觉一致性与语义彩色图标

## 目标

在固定 Kiyori 浅色、深色与跟随系统主题已经成为唯一应用主题方案后，继续清理页面内部残留的
浅色硬编码、单一蓝色图标和黑白灰工具矩阵。建立一套全应用可复用的语义色令牌，让颜色稳定
表达功能和状态，而不是被用户全局配色或角色外观改变。

本阶段重点覆盖：

- 负一屏的数据卡片和快捷工具
- AI 对话页的模态左抽屉、快捷入口、包管理、权限和工作流入口
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
- [DONE] 软件首页天气图标按天气与异常状态使用暖黄、蓝、青、紫、红、橙
- [DONE] AI 顶栏浏览器、终端、工作区动作固定使用蓝、青、紫语义色
- [DONE] App Shell 底部五入口的选中态改为暖黄色填充和页面背景色细节，不再使用蓝色空心描边
- [DONE] 本轮定向测试 `56/56`、资源/Kotlin 编译、formal readiness、差异检查和 Debug APK 通过
- [PENDING] 目标设备浅深色、窄屏徽标间距和五入口切换动画验收

本轮 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482617228` 字节，
SHA-256 `3C66F7F978051D9E8096EF794C274F9DC4375C93F6660EC7A8EE1468DA2138B8`；包名
`com.kiyori`、版本 `45 / 0.1.0`，Android Debug V2 签名和 16 KB ZIP 对齐通过。

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
