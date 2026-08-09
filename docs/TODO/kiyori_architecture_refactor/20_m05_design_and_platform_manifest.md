---
status: sealed
plan_version: 3
milestone: M-05
submilestone: M-05E sealed; M-05 complete
baseline: 176f803e683307aa8e182fb357f35f84755c5f8b + M-04 working tree
device_scope: excluded
last_reviewed: 2026-08-02
---

# M-05 Design 与 Platform 精确实施清单

## 当前结论

M-04 已完成根组合、Shell、MainActivity 内部 host 与稳定 launcher 总封板。M-05 负责把
Kiyori 的纯设计合同和跨领域 Android 平台能力迁入明确 owner，但不能把旧
`com.ai.assistance.operit.ui.theme`、`core/application` 或 `util` 整体搬迁。

只读审计确认旧 theme 目录的 11 个文件混合了五类职责：

- Kiyori 固定浅深 ColorScheme、Browser theme、Settings theme 和 semantic tone
- 根主题对 `UserPreferencesManager` 的状态读取
- Application system-bar、edge-to-edge 与状态栏隐藏副作用
- AI 对话局部字体、文字排版和背景 owner
- Liquid/Water glass 渲染实现

因此 M-05 必须按 owner 串行拆分。纯设计文件不读取偏好、不持有业务状态、不执行 Android
window/lifecycle/permission/storage 副作用；platform 文件不拥有主题、feature 或 Operit AI
业务状态。

## M-05 子里程碑

### M-05A1：纯 ColorScheme、Browser theme 与 Settings theme

目标：

- 新建 `com.kiyori.design.theme`
- 让固定 Kiyori application/browser 浅深 ColorScheme 由唯一纯 design 文件持有
- 把 `KiyoriBrowserTheme` 与 `KiyoriSettingsTheme` 移入 design owner
- 保持旧 theme preference adapter、根主题、system-bar、Typography、semantic tone 和 glass
  原 owner

本切片不改颜色、Typography、Shapes、CompositionLocal 默认失败语义、Browser 中性色域、
Settings 页面层级或任何用户偏好。

### M-05A2：Kiyori semantic design

迁移 `KiyoriSemanticTone`、固定浅深 icon/container pair、稳定 ID tone 映射、底部导航黄色与
天气太阳色。该切片单独处理 54 个生产消费者、4 个测试消费者与 102 条显式 import，不能
夹入 A1。

### M-05A3：根主题命名与 system-bar owner

先分离：

- 纯 `KiyoriTheme` Material/背景组合
- app host 的偏好投影
- platform Android system-bar/edge-to-edge 副作用

随后才处理 `OperitTheme -> KiyoriTheme` 与 `Theme.Operit -> Theme.Kiyori`。Application
system-bar owner 与 PlayerActivity system-bar owner 必须保持分离；不得把 Player 全屏行为迁入
全局主题。

### M-05B：platform logging

`AppLogger` 当前有 359 个文件、4407 次引用，并拥有 system log、内部日志文件、ToolPkg
package log、单线程写入和导出入口。迁移时保留旧 FQCN 为唯一兼容 facade，真实状态和写入
实现只能位于一个 platform logging owner；不得复制 executor、文件引用、context 或
`enableFileLogging` 状态。

### M-05C：platform lifecycle

`ActivityLifecycleManager` 当前有 11 个消费者，并混合：

- current Activity 弱引用
- foreground/background 计数
- keep-screen-on 请求计数
- Operit plugin lifecycle dispatch
- AI foreground microphone
- Player crash、VirtualDisplay 与 Shower 清理

必须先把纯 Android lifecycle fact 与 Operit integration side effect 分开，再移动 owner。
不能把现有对象原样放入 platform。

### M-05D：Android permission capability

MainActivity startup notification permission 可迁入窄 platform Android capability。
`AndroidPermissionPreferences` 是设置/执行模式的持久 owner，不是平台权限事实，不随该切片
移动。系统 grant/rationale 仍是唯一运行时事实源。

### M-05E：paths 与 storage

`OperitPaths` 当前有 46 个文件、98 次引用；`OperitBackupDirs` 有 7 个文件、33 次引用。
目录字符串、DataStore/backup/raw snapshot、public `Download/Kiyori` 与内部目录结构均为稳定
合同。先建立 `KiyoriPaths` 唯一真实 owner，再按领域拆 Browser、Player、Backup 和 Operit
consumer；旧入口只允许单一委派，不得建立第二目录计算实现。

## M-05A1 精确文件清单

### 新 design owner

```text
app/src/main/java/com/kiyori/design/theme/
├── KiyoriColorSchemes.kt
├── KiyoriBrowserTheme.kt
└── KiyoriSettingsTheme.kt
```

`KiyoriColorSchemes.kt` 唯一持有：

- `KiyoriBrowserLightColorScheme`
- `KiyoriBrowserDarkColorScheme`
- `KiyoriLightColorScheme`
- `KiyoriDarkColorScheme`
- `resolveKiyoriColorScheme(darkTheme: Boolean)`

`KiyoriBrowserTheme.kt` 与 `KiyoriSettingsTheme.kt` 是纯 Compose design wrapper，不读取
Context、DataStore、SharedPreferences、repository、ViewModel 或 Application。

### 旧路径处理

| 当前文件 | M-05A1 动作 | 保留职责 |
| --- | --- | --- |
| `ui/theme/ThemeColorSchemeResolver.kt` | `SPLIT` | 只保留 `Context + ThemePreferenceSnapshot` 到 dark/light bool 的 Operit preference adapter，并委派给 design resolver |
| `ui/theme/KiyoriBrowserTheme.kt` | `MOVE-KIYORI` | 旧路径删除 |
| `ui/theme/KiyoriSettingsTheme.kt` | `MOVE-KIYORI` | 旧路径删除 |
| `ui/theme/Theme.kt` | `KEEP` | A1 当时保留根偏好读取、system-bar 与 glass host；后续 M-05A3 已完成拆分并删除旧路径 |
| `ui/theme/KiyoriSemanticTheme.kt` | `KEEP` | 等待 M-05A2 |
| `ui/theme/Type.kt`、`TextLayoutSettings.kt` | `KEEP` | A1 当时保留 AI/全局字体职责；后续 M-05A3 只迁出固定 Typography 与纯字体应用 |
| `ui/theme/LiquidGlass.kt`、`WaterGlass.kt`、`AppBackgroundLayer.kt` | `KEEP` | 真实 AI/host presentation owner 后续单独审计 |

### 生产消费者

M-05A1 只更新以下消费者的 import，不改调用顺序或参数：

```text
app/src/main/java/com/kiyori/app/shell/KiyoriAppShell.kt
app/src/main/java/com/ai/assistance/operit/ui/features/player/PlayerActivity.kt
app/src/main/java/com/ai/assistance/operit/ui/features/websession/browser/WebSessionBrowserScreen.kt
app/src/main/java/com/ai/assistance/operit/ui/main/components/AppContent.kt
app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriCollapsingSettingsPage.kt
app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriSettingsHomePage.kt
app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriSettingsUi.kt
app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/theme/ThemeSettingsContentEditor.kt
app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/theme/ThemeSettingsTabs.kt
app/src/main/java/com/ai/assistance/operit/ui/floating/FloatingWindowTheme.kt
app/src/main/java/com/ai/assistance/operit/ui/theme/ThemeColorSchemeResolver.kt
```

Operit UI presentation 可以依赖纯 `com.kiyori.design`，以便在 Kiyori 宿主中共享视觉合同；
该许可不扩展到 Operit api/core/data/integrations/plugins/provider/services/util/widget。

### 测试

新增：

```text
app/src/test/java/com/kiyori/design/theme/KiyoriDesignThemeTest.kt
```

迁入现有固定色值、Browser 中性色域、Settings 浅深层级和 bool resolver 测试。旧
`KiyoriThemeTest` 继续保留 semantic tone、稳定 ID、天气/导航色和 Typography 测试，直到
M-05A2/A3。

## ARCH040 失败优先门禁

生产 design source 出现前先增加 ARCH040 与正反向 fixture。真实工作树必须先稳定失败于：

```text
ARCH040 M-05A1 design theme missing:
app/src/main/java/com/kiyori/design/theme/KiyoriColorSchemes.kt
```

ARCH040 必须锁定：

1. 三个 design source 的路径、package、LF-normalized SHA-256 与精确项目 import
2. 五个 ColorScheme/resolver symbol、Browser theme、Settings colors/local/theme/resolver
   的唯一声明
3. 旧 `KiyoriBrowserTheme.kt`、`KiyoriSettingsTheme.kt` 与旧 ColorScheme 声明清零
4. design source 不出现 preference、DataStore、SharedPreferences、Application、Activity、
   Window、system bar、lifecycle、permission、storage、repository、ViewModel 或 Operit import
5. `ThemeColorSchemeResolver` 保留两个现有 overload；bool overload 只委派
   `resolveKiyoriColorScheme`，Context/snapshot overload 的浅深决策不变
6. 13 个生产消费者全部使用新 package，旧 package 对已迁移 symbol 的 import 为 0
7. `operit-ui` 只新增 `com.kiyori.design` allowed root；其他 Operit ownership record 不变
8. 新测试包镜像 production owner，固定颜色和 contrast 断言不减少

正向 fixture 证明精确纯 design cluster 通过；反向 fixture 至少覆盖：

- design source 读取 preference
- 旧/new 双 owner
- Operit core 获得 design 依赖
- consumer 继续导入旧路径
- bool resolver 不再委派唯一 design resolver
- 固定色值或测试断言被删除

## M-05A1 封板证据

M-05A1 已于 2026-08-02 完成本地封板：

- `KiyoriColorSchemes.kt`、`KiyoriBrowserTheme.kt` 与 `KiyoriSettingsTheme.kt` 已成为
  唯一纯 `com.kiyori.design.theme` owner
- 三份 LF-normalized SHA-256 分别为
  `B9B650B076AF71FE2407561B8645864DCAD41087FBC7158B4BE07E573C6A5F2C`、
  `F0768F8E1FCDE737C3CC9A642D122556E1EC19A4DA22F36769BF026272ABCD00` 与
  `12079306E3546980B85854A548A75546DE6694E1359E6715224024C1019FA9A4`
- 两个旧 Browser/Settings theme 文件已删除；`ThemeColorSchemeResolver` 只保留两个既有
  overload 和偏好浅深决策，并由 bool overload 唯一委派 `resolveKiyoriColorScheme`
- 初始 11 个生产消费者和新旧测试所有权已迁移；2026-08-09 浏览器文字大小与网站密码管理
  两个设置子页继续复用 `LocalKiyoriSettingsColors`，ARCH040 精确 consumer 合同同步增至
  13 个。只有 `operit-ui` 获得 `com.kiyori.design` allowed root
- ARCH040 在生产 source 出现前真实失败于缺少 `KiyoriColorSchemes.kt`；实现后的正反向
  fixture 和真实 gate 均通过
- M-05A1 批准的两条 design import 使 M-04B App Shell checker-normalized hash 更新为
  `8F88F3C31A63E821EF118F00ECCD9B71643EF866BE3C2CBCB65F14CBEACC0D9F`。ARCH024
  仍精确比较完整项目 import 集合，当前为九条 Operit import 与两条 Kiyori design import
- ARCH024/ARCH040 联合定向测试 `4/4` 通过，完整 architecture `phase=m03` 通过
- 完整 Python `154/154` 通过；完整 JVM 为 `135 suites / 810 tests`，零 failure、
  error 和 skipped；formal readiness 通过
- lint 结构化交集为 `5792 retained / 0 stale / 316 current-only`，正式 baseline
  SHA-256 保持
  `A71AB39486275083A30C1DB51F0533162E2DB9054BE8D00BAD1EB2878FE8CE4C`；
  M-05A1 影响文件在临时完整 lint 中为 0 issues
- working-tree Markdown 为 256 files、0 issues；`git diff --check` 通过；Git index
  为空，terminal gitlink 保持
  `8d5c2c224317c0176c14520facbb202a8be5993f`
- 规定的 `:app:assembleDebug` 为 `233 actionable tasks`，其中 `28 executed`、
  `205 up-to-date`；`verifyDebugPlayerRuntimePackaging` 实际执行
- 最终 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，大小 `471291682` bytes，
  SHA-256 为
  `B8CD99D49D3F93745282C4E04F7FD7C158A875BE8897FF17161EC4D2C7F93646`
- APK 为 `com.kiyori`、`45 / 0.1.0`、label `Kiyori`、min/target/compile SDK
  `26 / 34 / 36`；Application 为 `com.kiyori.app.KiyoriApplication`，launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，`:crash / :repair / :player` 保持
- APK 只含 `arm64-v8a`，共有 53 个 native library、0 个重复 basename；Debug v2 signing
  为 true，`zipalign -c -P 16 -v 4` 通过

未提交、未推送、未操作 terminal、设备、模拟器或 ADB。UI、Browser、Player、多进程和视觉
实机验收继续为 `verification_pending`。该封板点随后进入 M-05A2 semantic design discovery。

## M-05A2 精确设计

### 只读基线

实施前唯一 owner 是 89 行的：

```text
app/src/main/java/com/ai/assistance/operit/ui/theme/KiyoriSemanticTheme.kt
```

LF-normalized SHA-256 为
`FE2179A3514E427AAEDFB07875C065F96881F6DCCBB41C6A3496D4FE3DE4FB2E`。文件混合两类
design 职责：

- 纯 enum/data/color contract：`KiyoriSemanticTone`、稳定 ID 映射、
  `KiyoriSemanticColors`、浅深 resolver、底栏黄色和天气太阳色
- Compose theme adapter：根据当前 `MaterialTheme` background luminance 投影浅深 resolver

实施前统计：

- `KiyoriSemanticTone`：56 个文件、509 次出现
- 完整迁移面：54 个生产消费者、4 个测试消费者
- 旧 package 显式 import：102 条，其中 `KiyoriSemanticTone` 54 条、`resolveColors`
  44 条、稳定 ID resolver 2 条、底栏颜色 1 条、天气 Composable 1 条
- 生产消费者分布为 3 个 Kiyori App Shell 文件与 51 个 Operit UI 文件；没有 Operit
  api/core/data/services/plugin/provider/util/widget 消费者

### 目标 owner

```text
app/src/main/java/com/kiyori/design/theme/
├── KiyoriSemanticColors.kt
└── KiyoriSemanticTheme.kt
```

`KiyoriSemanticColors.kt` 唯一持有：

- `KiyoriSemanticTone`，枚举顺序保持
  `BLUE/GREEN/PURPLE/ORANGE/RED/CYAN/PINK`
- `kiyoriSemanticToneForStableId(stableId)`，保持
  `Math.floorMod(stableId.hashCode(), entries.size)`
- `KiyoriSemanticColors(icon, container)`
- `resolveKiyoriSemanticColors(tone, isDark)` 的 14 组固定浅深 pair
- `KiyoriBottomNavigationSelectedFillColor = Color(0xFFFFC153)`
- `resolveKiyoriWeatherSunColor(false/true) = #C57C00/#FFD166`

该文件不 import Compose MaterialTheme，不读取 preference，不持有状态，不执行 Android 或
platform 副作用。

`KiyoriSemanticTheme.kt` 只持有：

- `@Composable fun KiyoriSemanticTone.resolveColors()`
- `@Composable internal fun kiyoriWeatherSunColor()`

两个 adapter 都以
`MaterialTheme.colorScheme.background.luminance() < 0.5f` 判断暗色，并唯一委派纯 resolver。
adapter 不声明色值、枚举、data class、缓存、Flow、remember state 或第二映射。

旧 `com.ai.assistance.operit.ui.theme.KiyoriSemanticTheme.kt` 删除，不保留 typealias、
compatibility facade 或转发函数。Kiyori 尚未发布，且全仓库搜索没有脚本、反射、序列化、
Manifest、AIDL、JNI 或外部协议依赖该旧 FQCN。

### 消费者与测试

新增：

```text
config/architecture/m05a2-semantic-consumer-imports.txt
config/architecture/m05a2-semantic-colors-sha256.txt
config/architecture/m05a2-semantic-theme-sha256.txt
```

2026-08-08 浏览器 UI 迭代后，ARCH041 消费者快照按实际依赖演进为 `99` 条导入、`51` 个生产
消费者路径和 `4` 个外部测试路径。浏览器四行菜单、占位页、书签新增弹窗、UA/下载身份色和负一屏
入口色改用浏览器局部 `WebSessionBrowserMenuTone` 后不再伪装成全应用语义色消费者；窗口总览及其
视觉策略测试成为新的真实消费者。`KiyoriSemanticColors` 与 `KiyoriSemanticTheme` 的 owner、
源码哈希、七色枚举和解析行为均未改变。

consumer snapshot 逐条保存 `path + imported symbol`，必须与当前全部 99 条项目 import 精确
相等。所有旧 package import 和完全限定旧 FQCN 清零；不改变调用表达式、参数、tone 选择、
页面层级、Compose 嵌套或状态 owner。

`KiyoriDesignThemeTest` 接收 A2 的三组纯合同测试：

- 浅深 semantic pair 唯一、变化且 icon/container 对比度不低于 3:1
- 底栏 `#FFC153` 与天气 `#C57C00/#FFD166` 精确且互相独立
- 稳定 entry ID 两次映射一致且样本覆盖至少五个 tone

旧 `KiyoriThemeTest` 删除这三组测试和仅为它们存在的颜色/对比度 helper，只保留 Typography
合同。其余 `KiyoriShellStateTest`、`KiyoriSettingsPagesTest` 与
`PackageManagerVisualPolicyTest` 只更新 import。

### ARCH041 failure-first

ARCH041 必须在新增 production source 前由正反向 fixture 建立，并让真实工作树只失败于：

```text
ARCH041 M-05A2 semantic design missing:
app/src/main/java/com/kiyori/design/theme/KiyoriSemanticColors.kt
```

ARCH041 锁定：

1. 两个 source 的路径、package、LF-normalized SHA、精确项目 import
2. 纯文件的枚举顺序、稳定 ID 算法、data class、14 组 pair、底栏和天气色值
3. adapter 的两个 Composable、`MaterialTheme` luminance `< 0.5f` 与唯一委派
4. 旧路径、旧 package import、旧完全限定 FQCN和双 owner 清零
5. 54 个生产、4 个测试消费者和 102 条 import 与 snapshot 完全一致
6. semantic 测试迁入 design test，旧 theme test 只保留 Typography
7. Operit design 依赖仍只位于 `operit-ui`，不扩大 ownership
8. ARCH025/ARCH026/ARCH027 对 AI Drawer、Primary Navigation 与 Software Home 的
   normalized hash、完整项目 import 和其余既有合同继续通过

正向 fixture 证明 split owner 与消费者 snapshot 通过；反向 fixture 至少覆盖：

- pure source import MaterialTheme 或读取 preference
- adapter 持有 raw Color literal、remember/Flow state 或偏离 `0.5f`
- 枚举顺序、任一固定 pair、稳定 ID 算法、底栏或天气色漂移
- 旧/new 双 owner、旧 import 或完全限定旧 FQCN
- consumer snapshot 缺失、新增、重复或路径漂移
- semantic 测试断言被删除，或旧 theme test 继续持有 semantic 测试
- 非 UI Operit ownership 获得 design permission

### A2 非目标与停止条件

- 不改 `OperitTheme`、`Theme.Operit`、Typography、Shapes、ColorScheme 或 preference host
- 不改任何 tone 选择、页面颜色语义、底栏动画、天气图标逻辑、Browser 中性 chrome
- 不迁 system-bar、edge-to-edge、AppLogger、lifecycle、permission 或 paths
- 不新增 alias、facade、fallback、状态 owner、缓存、DataStore、repository 或 module
- 不操作 terminal、设备、模拟器、ADB、提交或推送

回滚点是 M-05A1 已封板工作树。任一固定色值、枚举顺序、稳定映射、luminance 判断、消费者
集合或既有视觉测试发生非 package-only 漂移时，停止 A2 实现并定位根因，不扩大 snapshot。

## M-05A2 封板证据

M-05A2 已于 2026-08-02 完成本地封板：

- 新增 `KiyoriSemanticColors.kt` 与 `KiyoriSemanticTheme.kt`，LF-normalized SHA-256 分别为
  `F4F74610C81D3080B2BA085B5D2CD20F3BC6249B8DBCE75A7786899AB9B716D2` 与
  `1E0FD1A5E0FA494776F141DDE80D4C454DBB6EE7275B703B872605860B5AC9BE`
- 旧 `ui/theme/KiyoriSemanticTheme.kt` 已删除；没有 facade、typealias、fallback 或第二 owner
- 54 个生产消费者、3 个外部测试消费者的 102 条 import 与 consumer snapshot 精确相等；
  第 4 个测试消费者为同 package 的 `KiyoriDesignThemeTest`，无需 import
- `PlayerScreen.kt` 唯一完全限定 `KiyoriSemanticColors` 引用已迁入新 package；旧 package
  import 与旧完全限定 FQCN 均为 0
- AI Drawer、Primary Navigation 与 Software Home 的批准 semantic import 和源码 hash 已同步，
  ARCH025/026/027/040/041 真实门禁全部通过
- failure-first 真实工作树仅报告缺少 `KiyoriSemanticColors.kt`；实现后的 10 个受影响
  architecture 正反向测试全部通过
- 完整 architecture `phase=m03` 通过；完整 Python `156/156`、JVM
  `135 suites / 810 tests` 均为零 failure/error/skipped；formal readiness 与 fresh-clone 通过
- 新鲜 full lint 报告 `27 errors / 287 warnings / 2 hints`，baseline 交集保持
  `5792 retained / 0 stale / 316 current-only`。两个新 design 文件 0 命中；唯一受影响路径
  命中是 `WebSessionUserscriptSheet.kt` 未改正文的 5 个既有诊断，A2 实际改动行无 lint 回归
- 规定 `:app:assembleDebug` 为 `233 actionable tasks`，其中 `28 executed`、`205 up-to-date`，
  `verifyDebugPlayerRuntimePackaging` 实际执行
- APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，大小 `471292358` bytes，SHA-256
  `60D613A5F9BFBD019296E21E05547DF16B0789FCDE73FF2699AFBAC415A8BBC0`
- APK 为 `com.kiyori`、`45 / 0.1.0`、label `Kiyori`、SDK `26 / 34 / 36`；Application 为
  `com.kiyori.app.KiyoriApplication`，launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，`:crash / :repair / :player` 保持
- APK 只含 `arm64-v8a`，共有 53 个 native library、0 个重复 basename；Debug v2 signing
  与 `zipalign -c -P 16 -v 4` 通过

未提交、未推送、未操作 terminal、设备、模拟器或 ADB。UI、Browser、Player、多进程和视觉
实机验收继续为 `verification_pending`。当前进入 M-05A3 root theme、preference host 与
system-bar owner discovery。

## M-05A3 精确设计

### 只读基线

旧 root theme 是 144 行的：

```text
app/src/main/java/com/ai/assistance/operit/ui/theme/Theme.kt
```

LF-normalized SHA-256 为
`C6F558659CF1CEFD6994B1C604493C1F8E22FEBBF4223C7D4EF12800209F293F`。它同时持有：

- `UserPreferencesManager` 的八个 theme/font/status-bar Flow 与 initial 值
- `isSystemInDarkTheme` 的浅深模式投影
- 配置字体创建
- Application edge-to-edge、status-bar hide/show、navigation contrast
- MaterialTheme、根背景、Liquid/Water Glass state 与 CompositionLocal

生产调用者只有：

```text
app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt
app/src/main/java/com/ai/assistance/operit/widget/ToolPkgDesktopWidgetConfigActivity.kt
```

资源 `Theme.Operit` 在 `values/values-night/values-v27/values-night-v27/values-v29/
values-night-v29` 各声明一次，Manifest 在 application、Player、MainActivity、Crash、
Recovery、Widget Config 共引用 6 次。Kiyori 未发布，且没有序列化、反射、AIDL、JNI 或外部
协议依赖这些内部 Compose/resource 名。

`Type.kt` 为 180 行，混合固定零 tracking Typography、纯字体应用、UserPreferences 字体常量、
Context/File/Uri 字体加载与 AppLogger。`applyFontFamilyToTypography` 有 3 个外部生产
消费者；`Typography` 有 `OperitUtilityTheme` 与 `FloatingWindowTheme` 两个显式旧 import。
旧 `KiyoriThemeTest` 只剩零 tracking 测试。

PlayerActivity 独立拥有 fullscreen system-bar，LF-normalized SHA-256 为
`AEF88E8F34DD08098D858E4E5D3F36CBF1867AE36E6C44B96346F6A0BC11A756`。A3 不修改该文件；
QD-04 后续平台合同批次在保留 fullscreen system-bar owner 的前提下更新了当前
ARCH042 PlayerActivity hash，见架构门禁规范中的 QD-04 记录。

### 目标 owner

```text
app/src/main/java/com/kiyori/
├── design/theme/KiyoriTheme.kt
├── design/theme/KiyoriTypography.kt
├── app/theme/KiyoriTheme.kt
└── platform/window/KiyoriApplicationSystemBars.kt
```

纯 design `KiyoriTheme` 接收已解析的 `ColorScheme`、标准命名的背景 effect `modifier` 与
`Typography`，保持原来的
`fillMaxSize -> background(colorScheme.background) -> layer/liquid effect -> content`
顺序。它不读取 preference、不创建 Glass state、不执行 Android window/lifecycle/system-bar
副作用，也不 import Operit。

`KiyoriTypography` 唯一持有 Material3 base、15 个零 tracking style 与
`applyFontFamilyToTypography`。旧 Type 继续持有 `getSystemFontFamily`、
`loadCustomFontFamily`、`resolveConfiguredFontFamily` 和 `createCustomTypography`，但改为
委派唯一 design Typography；不复制字体解析或文件加载实现。

app package 的 `KiyoriTheme(content)` 是唯一 preference/字体/Glass host。它保持八个 Flow、
全部 initial 值、`remember` key、dark-theme 判定、`rememberLayerBackdrop`、
`isWaterGlassSupported/rememberLiquidState`、两个 CompositionLocal 和原 effect Modifier 顺序，
然后分别委派 pure design theme 与 platform system-bar。

`KiyoriApplicationSystemBars` 接收 `darkTheme`、navigation background 和
`statusBarHidden`，独占旧 Theme.kt 的 SideEffect；保持透明 status bar、浅深
`SystemBarStyle`、`enableEdgeToEdge`、status-bar hide/show、transient swipe 与 API 29
navigation contrast。它不读取 preference，不 import Operit，不触碰 Player。

### 精确迁移与 ownership

- 删除旧 `ui/theme/Theme.kt`，不保留 `OperitTheme` facade/typealias/转发函数
- MainActivity 与 Widget Config 各把唯一 import/call 改为
  `com.kiyori.app.theme.KiyoriTheme`
- Widget 通过文件精确 ARCH001 exception 复用 app host；`operit-widget` ownership 不整体
  放宽到 `com.kiyori.app`
- app host 对现有 UserPreferences/Type/LiquidGlass/WaterGlass 的过渡依赖使用文件精确
  ARCH004 exception；`kiyori-app` 默认 allowed roots 不放宽
- `kiyori-platform` 从 planned 转为 M-05A3 active owner，只允许 capability/platform root
- `OperitUtilityTheme`、`FloatingWindowTheme` 与三个
  `applyFontFamilyToTypography` 消费者迁入 design import；
  `resolveConfiguredFontFamily` 继续从旧 Type owner 导入；消费者快照精确保存 7 条
  `path + imported symbol`
- 零 tracking 测试迁入 `KiyoriDesignThemeTest`，旧 `KiyoriThemeTest` 删除
- 6 个 style 声明与 Manifest 6 个引用精确改名为 `Theme.Kiyori`；保留
  `KiyoriThemeBase`、全部 item/parent 与组件集合
- 更新 m03 semantic Manifest hash 与 ARCH039 MainActivity 完整项目 import snapshot

### ARCH042 failure-first

ARCH042 在新增 production owner 前先以正反向 fixture 建立，真实工作树必须只失败于：

```text
ARCH042 M-05A3 pure root theme missing:
app/src/main/java/com/kiyori/design/theme/KiyoriTheme.kt
```

ARCH042 锁定：

1. 四个新 source、Type/Liquid/Water/Player 受影响边界的路径、package、SHA 与精确项目 import
2. pure theme 的参数、Material/background/effect/content 顺序与禁用依赖
3. KiyoriTypography 的 15 个零 tracking style、纯 apply 函数和唯一声明
4. app host 八个 Flow、initial 值、remember key、dark 判定、Glass state/local 与两条委派
5. platform owner 的全部 system-bar 行为、唯一声明与 preference/Operit 零依赖
6. 旧 Theme.kt、OperitTheme、旧 Typography/apply owner、旧 theme test 清零
7. MainActivity/widget 唯一 host import/call、两个精确 ownership exception 与 ARCH039 snapshot
8. 6 个 `Theme.Kiyori` 声明、Manifest 6 个引用、旧 style 清零和 m03 semantic hash
9. PlayerActivity 原 hash与 fullscreen system-bar token 保持
10. Liquid/Water Glass 算法与 capability owner不变

反向 fixture 至少覆盖 pure design 读取 preference、app host 少订阅一个 Flow、第二 system-bar
owner、旧 facade/style 残留、Widget 获得宽泛 app permission、Typography 双 owner、
Manifest/style 数量漂移和 Player hash 改动。

### A3 非目标与停止条件

- 不改固定 ColorScheme、semantic tone、Shapes、字体选择/缩放算法或 Glass 视觉算法
- 不改 AI 局部字体、TextLayoutSettings、背景媒体、Bubble/Avatar 或 ThemePreferenceSnapshot
- 不改 PlayerActivity、Player process、fullscreen Surface/window 行为
- 不删除仍被 WebChat、图片生成和 UtilityTheme 使用的 ThemeColorSchemeResolver
- 不迁 AppLogger、lifecycle、permission、paths/storage
- 不新增 alias、facade、fallback、第二 preference、第二 Glass state 或第二 system-bar owner
- 不操作 terminal、设备、模拟器、ADB、提交或推送

回滚点是 M-05A2 已封板工作树。任一颜色、Typography style、Glass modifier 顺序、偏好
initial 值、Manifest 组件集合、Player hash 或 system-bar 行为发生非 owner-only 漂移时，停止
A3 实现并定位根因，不扩大 snapshot。

## M-05A3 封板证据

M-05A3 已于 2026-08-02 完成本地封板：

- 新增纯 `com.kiyori.design.theme.KiyoriTheme`、唯一 `KiyoriTypography`、唯一
  `com.kiyori.app.theme.KiyoriTheme` preference/font/Glass host 与唯一
  `com.kiyori.platform.window.KiyoriApplicationSystemBars`
- 四个新 owner 的 LF-normalized SHA-256 分别为
  `E1FF66462BD373AE77AEC65FB358653160491C644EC0A2630268F043EC0AD514`、
  `617AC6645725FBBFD5ACB3C99CDE74F8CE7E3D76856264F2B4ADD8E186820A35`、
  `5059698BA23D6840C050AF43584A27C5BD01D8652037BB68C292A553FFCE76BA` 与
  `3E64952A32480BF8AACEE172AF46138CAD3EA7F268DBEF76111EE9A4C82075BD`
- 旧 `ui/theme/Theme.kt`、`OperitTheme`、`Theme.Operit`、旧 Typography/apply owner 与旧
  root-theme test 已清零；没有 facade、typealias、fallback 或第二状态 owner
- MainActivity 与 Widget Config 各只导入并调用一次同一 app host；文件精确 ARCH004/ARCH001
  exception 被完整 ownership gate 消费，`kiyori-app` 与 `operit-widget` 均未被宽泛放权
- 6 个 `Theme.Kiyori` 声明、Manifest 6 个引用、七条 design consumer import、m03 Manifest
  semantic hash 与 ARCH039 MainActivity import snapshot 全部精确一致
- PlayerActivity LF-normalized SHA-256 仍为
  `AEF88E8F34DD08098D858E4E5D3F36CBF1867AE36E6C44B96346F6A0BC11A756`；
  fullscreen system-bar、Liquid/Water Glass 算法和用户偏好 initial 值未改变
- ARCH040/041/042 六个正反向定向测试通过，真实 ARCH042 为 0 错误；完整 architecture
  `phase=m03` 与完整 Python `158/158` 通过
- 完整 JVM XML 为 `134 suites / 810 tests / 0 failures / 0 errors / 0 skipped`；
  formal readiness、fresh-clone readiness 与 `git diff --check` 通过
- fresh full lint 为既有 `27 errors / 287 warnings / 2 hints`；四个新 owner 为 0 命中，
  A3 受管范围仅有 Manifest 两条与 MainActivity 一条既存 warning，早期两个 Modifier warning
  已清零；baseline 交集保持 `5792 retained / 0 stale / 316 current-only`
- 规定 `:app:assembleDebug` 为 `233 actionable tasks`，其中 `28 executed`、
  `205 up-to-date`，`verifyDebugPlayerRuntimePackaging` 实际执行
- APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，大小 `477957302` bytes，SHA-256
  `9373518AEB8FA8BD2C02DCFDE5741833653A2D76AFE270D2C27F4D2BF88C0074`
- APK 为 `com.kiyori`、`45 / 0.1.0`、label `Kiyori`、SDK `26 / 34 / 36`；Application 为
  `com.kiyori.app.KiyoriApplication`，launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，`:crash / :repair / :player` 保持
- APK 只含 `arm64-v8a`，共有 53 个 native library、0 个重复 basename；Debug v2 signing
  与 `zipalign -c -P 16 -v 4` 通过

未提交、未推送、未操作 terminal、设备、模拟器或 ADB。UI、Browser、Player、多进程和视觉
实机验收继续为 `verification_pending`。M-05B 随后进入 logging discovery。

## M-05B 精确设计

### 只读基线与兼容证据

实施前唯一实现是：

```text
app/src/main/java/com/ai/assistance/operit/util/AppLogger.kt
```

实测 `AppLogger` 覆盖 359 个 Kotlin/Java 文件、4407 次引用，显式旧 package import 为
57 个。该对象同时拥有：

- Android system `Log` 调用和原返回值
- `files/logs/operit.log`
- `Download/Kiyori/packageLogs/<startup timestamp>.log`
- `OperitAppLogger` 单线程 executor
- `enableFileLogging`、早期绑定的内部 filesDir、两个日志文件引用
- ToolPkg package/script/plugin 标识提取
- 日志消息和 Throwable 文本截断
- `bindContext`、`getLogFile`、`resetLogFile` 与全部 Log 风格 API

旧 FQCN 不能删除。两个 JVM 测试直接执行
`Mockito.mockStatic(AppLogger::class.java)`；`MemoryDocumentsProvider` 在 Application
`onCreate` 之前通过 `bindContext` 建立早期内部日志目录；日志导出继续调用
`getLogFile/resetLogFile`。这些证据要求稳定入口保留，但不要求旧对象继续持有实现状态。

### 唯一 platform owner

新增：

```text
app/src/main/java/com/kiyori/platform/logging/
├── KiyoriLogger.kt
└── KiyoriLogTextFormatter.kt
```

`KiyoriLogger` 唯一持有 executor、提前解析的内部 filesDir、package-log root provider、
`logFile`、`packageLogFile` 和 `enableFileLogging`。进程 Context 继续只由既有
`ApplicationContextAccess` 持有；logger 不再静态保存 Android Context。全部 system log
方法保持先提交文件写入、再调用
对应 Android `Log` 并返回原 `Int` 的既有顺序；文件写入继续只在同一个 daemon 单线程执行。

以下合同保持逐字或逐值一致：

- priority：`VERBOSE/DEBUG/INFO/WARN/ERROR/ASSERT`
- 文件：`logs/operit.log`
- ToolPkg 目录：`packageLogs`
- 线程名：`OperitAppLogger`
- 时间格式：`yyyy-MM-dd HH:mm:ss.SSS` 与 `yyyyMMdd_HHmmss_SSS`
- 消息和异常上限：`12_000 / 24_000`
- ToolPkg tag、package/script/plugin 正则和行格式
- Application startup time 为 `0L` 时使用调用时刻的既有语义
- `resetLogFile` 只删除内部 app log，并清空两个缓存文件引用
- 文件日志关闭时 system `Log` 仍执行

原 `ThrowableTextFormatter` 只有 AppLogger 与 `CrashReportStore` 两个生产消费者，没有
反射、序列化、Manifest、AIDL、JNI、脚本或测试绑定证据，因此其唯一实现改名并迁入
`KiyoriLogTextFormatter`。`CrashReportStore` 直接使用新 owner，旧 formatter 文件删除；
不复制 cause depth、stack frame、循环检测、截断 marker 或最小文本实现。

### 旧 FQCN facade

```text
app/src/main/java/com/ai/assistance/operit/util/AppLogger.kt
```

旧对象保留全部常量、属性与 `@JvmStatic` 方法签名，只委派 `KiyoriLogger`。它不声明
`@Volatile` 状态，不创建 executor、formatter、正则、Context 或文件引用，也不执行文件写入。

Provider 的 `AppLogger.bindContext(context)` 继续可用；platform owner 立即从
`context.applicationContext.filesDir` 解析内部日志目录。M-05B 封板时 package-log root
provider 仍为 `OperitPaths::kiyoriRootDir`；M-05E 已把 Application 与 Provider 两个绑定点
同步改为 `KiyoriPaths::kiyoriRootDir`，旧 `OperitPaths` 只保留兼容委派。
`KiyoriApplication.onCreate` 在安装 `ApplicationContextAccess` 后执行同一绑定，确保普通
Application 启动不依赖 Provider 是否被外部访问。两个里程碑均不复制
`Download/Kiyori` 字符串或 `ensureDir` 算法。

### Kiyori 直接消费者

以下 8 个 Kiyori app owner 改为直接导入
`com.kiyori.platform.logging.KiyoriLogger`：

```text
app/src/main/java/com/kiyori/app/
├── KiyoriApp.kt
├── KiyoriApplication.kt
└── startup/
    ├── KiyoriMainDisplayCoordinator.kt
    ├── KiyoriMainNotificationPermissionCoordinator.kt
    ├── KiyoriMainOrientationCoordinator.kt
    ├── KiyoriMainSharedContentCoordinator.kt
    ├── KiyoriMainStartupGateCoordinator.kt
    └── KiyoriMainTaskVisibilityCoordinator.kt
```

Operit api/core/data/plugins/provider/services/ui/util/widget 消费者继续导入旧 facade，避免
359 个文件发生无必要的上游冲突。稳定旧包中的 `MainActivity` 也继续使用 facade；该选择
不创建第二 logger owner。

M-05B 删除 Display coordinator 已到期的唯一 logging ARCH004 exception。其余 coordinator
仍因 `R`、`SharedFileHandler`、`AIForegroundService`、preference 或 screen bridge 保留精确
例外，但原因文字不再把已迁移的 logging 列为过渡依赖。

### ARCH043 failure-first

ARCH043 在 production logger 出现前先由正反向 fixture 建立，真实工作树稳定只报告：

```text
ARCH043 M-05B platform logger missing:
app/src/main/java/com/kiyori/platform/logging/KiyoriLogger.kt
```

实现后 ARCH043 锁定：

1. platform logger、formatter、旧 facade 与 CrashReportStore 的 LF-normalized SHA-256
2. platform source 的 package、精确项目 import 和 Operit 零依赖
3. priority、文件/目录、线程名、时间格式、限制、regex、system/file/package log 路径
4. executor、提前解析的内部 filesDir、root provider、文件引用和 `enableFileLogging`
   只有一个真实 owner，logger 不静态持有 Context
5. 旧 facade 的常量、属性、全部方法委派和实现状态清零
6. 旧 formatter 路径清零，CrashReportStore 唯一导入新 formatter
7. 8 个 Kiyori 直接消费者与 snapshot 精确一致，Kiyori source 旧 logger import 为 0
8. KiyoriApplication 与 Provider 两个目录绑定入口保持
9. 两个旧 FQCN static-mock 测试继续存在
10. `kiyori-platform` ownership 不放宽，Display logging exception 清零
11. formatter 的 unchanged、bounded marker、cause/bound 三条 JVM 合同测试
12. ARCH018/020/032～037 的既有 hash、import、调用次数和 owner 合同同步保持

正向 fixture 证明唯一 implementation + facade 通过；反向 fixture 覆盖 platform 导入
Operit、facade 重新持有状态、日志文件名漂移、旧 formatter 残留、Kiyori consumer 回到旧
入口、static-mock 合同删除、formatter 断言删除和过期 exception 回流。

### M-05B 非目标

- 不改变任何日志 tag、message、priority、Android `Log` 调用、返回值或调用位置
- 不整体迁移 Operit 消费者，不批量重写 359 个文件
- 不修改 `OperitPaths` 的目录字符串或计算逻辑
- 不迁移 `ActivityLifecycleManager`、权限持久设置、Browser/Player/Backup path
- 不修改数据库、DataStore、备份/raw snapshot、Manifest、AIDL、JNI、namespace 或依赖
- 不创建第二 executor、第二日志文件、第二 Context owner 或第二 package-log formatter；
  `ApplicationContextAccess` 保持唯一进程 Context owner
- 不操作 terminal、设备、模拟器、ADB、提交或推送

### 风险与停止条件

| 风险 | 证据 | 停止条件 |
| --- | --- | --- |
| facade API 破坏 | 旧方法/常量 gate + 两个 static-mock 测试 | 旧测试无法编译或旧 FQCN 缺失 |
| 双写或乱序 | executor/state 唯一 owner gate | facade 出现 executor、FileWriter 或文件状态 |
| 日志格式漂移 | SHA、固定 token、formatter JVM tests | 文件名、格式、上限、cause/marker 改变 |
| package log 路径漂移 | OperitPaths provider 接线与 M-05E 边界 | platform 内出现第二 `Download/Kiyori` 计算 |
| Kiyori/Operit 依赖方向扩大 | ownership + 8-consumer snapshot | platform 导入 Operit 或新增未审计 Kiyori consumer |
| 旧里程碑合同失真 | ARCH018/020/032～037 | 仅为通过 M-05B 放宽旧 owner、调用次数或状态断言 |

回滚点是 M-05A3 已封板工作树；不使用 reset、clean 或 checkout。失败时只修复 M-05B
生产 owner、facade、formatter、8 个消费者、snapshot、门禁和对应文档。

### 分层验证

最窄验证：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05b_platform_logging_accepts_single_owner_and_facade `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05b_platform_logging_rejects_state_or_compatibility_drift

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.platform.logging.KiyoriLogTextFormatterTest `
  --no-daemon --console=plain
```

受影响旧门禁继续执行 ARCH018/020/032～037 的真实仓库检查和相关正反向 fixture。封板继续
执行完整 architecture、完整 Python、完整 JVM XML 统计、formal/fresh-clone readiness、
范围内 lint、working-tree Markdown、`git diff --check`、规定的 Debug APK 与制品审计。
设备和多进程文件写入实测继续为 `verification_pending`，不能由本地编译替代。

### M-05B 封板证据

- ARCH043 failure-first 先稳定得到缺少 `KiyoriLogger.kt` 的单一真实错误；正反向 fixture
  `2/2`、真实 ARCH043、ownership、ARCH018/020/032～037 和完整 architecture
  `phase=m03` 全部通过
- 完整 Python 为 `160/160`；完整 JVM XML 为
  `135 suites / 813 tests / 0 failures / 0 errors / 0 skipped`
- formal readiness 与 fresh-clone readiness 通过；working-tree Markdown 为
  `256 files / 0 issues`，`git diff --check` 无错误
- 第一份新鲜 full lint 暴露迁移后的 `KiyoriLogger.boundContext` 产生新
  `StaticFieldLeak`，同时旧 `AppLogger.kt` baseline 记录失效；该问题未通过 suppression 或
  baseline 搬运处理，而是把早期绑定状态收敛为 `context.applicationContext.filesDir`
  得到的 `File`
- 修正后 full lint 恢复既有 `27 errors / 287 warnings / 2 hints`；logger、formatter 与
  formatter test 三个新 owner 为 0 命中，唯一 M-05B 影响路径命中是 `KiyoriApp.kt`
  未改正文中的既有 `ConfigurationScreenWidthHeight` warning
- 临时完整 baseline 与正式 baseline 的结构化交集为
  `5791 retained / 0 stale / 316 current-only`；只删除旧 `AppLogger.kt` 已失效的
  `StaticFieldLeak`，不吸收 current-only 问题。归一化 SHA-256 为
  `aeb75aee42cf985ac5a8f6a33de88c04d1b0c50580e952e32542fe8403aab8c0`
- 规定的 `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `233 actionable tasks / 24 executed / 209 up-to-date`，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `477957302` bytes，SHA-256
  `3A9BCBC711DB1FB735C3828AA1751F80B347C8C1FC7E34E783F16FFD96D631A1`
- APK 为 `com.kiyori`、`45 / 0.1.0`、label `Kiyori`、SDK `26 / 34 / 36`；Application 为
  `com.kiyori.app.KiyoriApplication`，launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，`:crash / :repair / :player` 保持
- APK 只含 `arm64-v8a`，共有 53 个 native library、0 个重复 basename；10 个 player
  target library 各存在 1 份，Debug v2 signing 与 `zipalign -c -P 16 -v 4` 通过

未提交、未推送、未部署、未操作 terminal、设备、模拟器或 ADB。真实 Android system
`Log`、内部日志文件、ToolPkg package log、多进程文件写入和日志导出实测继续为
`verification_pending`。M-05B 本地封板完成，下一独立切片为 M-05C platform lifecycle。

## M-05C 精确实施清单

### 目标与边界

M-05C 只拆分 `ActivityLifecycleManager` 内已经存在的两类职责：

1. Android Activity lifecycle facts：当前 Activity 弱引用、已创建 Activity 数、已 started
   Activity 数、应用前后台状态，以及唯一 `ActivityLifecycleCallbacks` 注册
2. Operit integration side effects：keep-screen-on 偏好与请求计数、plugin lifecycle hook、
   External Chat 自启动、AI 麦克风前台、Player crash 协调、VirtualDisplay 与 Shower 清理

本切片不改变 MainActivity、Application 启动顺序、plugin event、External Chat reason、日志
tag/message、`2500L` 麦克风节流、`FLAG_KEEP_SCREEN_ON` 请求计数、最后 Activity 清理边界，
也不迁移通知权限、paths/storage、Browser、Player、数据库、Manifest、AIDL、JNI、namespace
或依赖。

### 兼容面实测

源码实测共 11 个 Kotlin/Java 相关文件：定义文件与 10 个调用或 import 文件。旧完整 FQCN
还出现在：

```text
app/src/main/java/com/ai/assistance/operit/core/tools/javascript/README.md
examples/java_bridge.js
examples/java_bridge.ts
```

实现前 `javap` 证明旧对象公开 ABI 为：

```text
ActivityLifecycleManager.INSTANCE
initialize(Application)
getCurrentActivity()
checkAndApplyKeepScreenOn(boolean)
forceKeepScreenOn(boolean)
onActivityCreated(Activity, Bundle)
onActivityStarted(Activity)
onActivityResumed(Activity)
onActivityPaused(Activity)
onActivityStopped(Activity)
onActivitySaveInstanceState(Activity, Bundle)
onActivityDestroyed(Activity)
implements Application.ActivityLifecycleCallbacks
```

因此旧 `com.ai.assistance.operit.core.application.ActivityLifecycleManager` 不删除、不改名、不缩减
接口，也不批量迁移 13 个 FQCN 引用路径。

### 唯一 owner

```text
app/src/main/java/
├── com/kiyori/platform/lifecycle/
│   └── KiyoriActivityLifecycle.kt
└── com/ai/assistance/operit/core/application/
    ├── ActivityLifecycleManager.kt
    └── OperitActivityLifecycleIntegration.kt
```

- `KiyoriActivityLifecycle` 是唯一 Android callback 注册 owner
- `KiyoriActivityLifecycleFacts` 是运行时唯一 current Activity、activity count、started count
  与 foreground boolean 状态实现；它不注册 callback，不导入项目代码，不执行副作用
- `KiyoriActivityLifecycleObserver` 只把已完成的 lifecycle 迁移结果交给 integration
- `OperitActivityLifecycleIntegration` 是唯一 keep-screen-on、plugin、AI、Player、
  VirtualDisplay 与 Shower 副作用 owner
- 旧 `ActivityLifecycleManager` 保留完整 object/interface/方法 ABI，只初始化并委派两个新
  owner，不再持有 Context、弱引用、计数、协程、偏好或副作用实现

integration 不保存第二 application Context。每个 Android callback 直接使用同一
`activity.applicationContext` 作为既有 plugin/External Chat 参数；`initialize(application)`
仍只初始化原 `ApiPreferences` owner。

### 回调时序合同

| Android callback | platform facts 先执行 | Operit integration 后执行 |
| --- | --- | --- |
| created | `activityCount += 1` | 原 created log，然后 `ACTIVITY_CREATE` |
| started | `startedActivityCount += 1` 并判定首次前台 | `ACTIVITY_START`，首次前台时 External Chat，然后 `APPLICATION_FOREGROUND` |
| resumed | 当前 Activity 弱引用指向本 Activity | PlayerCrash，2500 ms microphone ensure，然后 `ACTIVITY_RESUME` |
| paused | 仅当同一 Activity 时清除弱引用 | `ACTIVITY_PAUSE` |
| stopped | started count 向 0 收敛并判定最后 started Activity | `ACTIVITY_STOP`，最后 stopped 时 `APPLICATION_BACKGROUND` |
| destroyed | 仅当同一 Activity 时清除弱引用，`activityCount -= 1` | 原 destroyed log、`ACTIVITY_DESTROY`，count `<= 0` 时清理 VirtualDisplay/Shower |

keep-screen-on 的两组引用计数、用户偏好检查、当前 Activity 为空日志、UI-thread window 操作、
add/clear flag 与异常日志保持原实现和原执行顺序。

### ARCH044 failure-first

ARCH044 在 production owner 出现前先由正反向 fixture 建立；真实工作树稳定只报告：

```text
ARCH044 M-05C platform lifecycle owner missing:
app/src/main/java/com/kiyori/platform/lifecycle/KiyoriActivityLifecycle.kt
```

实现后 ARCH044 锁定：

1. platform facts、Operit integration 与旧 facade 三个 LF-normalized SHA-256
2. platform package、项目依赖为零以及 Operit side-effect token 为零
3. callback 注册、弱引用、activity/started count 与 foreground boolean 只有一个实现
4. started count 的 `coerceAtLeast(0)`、current Activity identity clear 与 destroyed
   `<= 0` 清理边界
5. integration 的精确项目 import、keep-screen-on 两组请求计数、plugin 八类事件、
   External Chat reason、PlayerCrash、2500 ms microphone、VirtualDisplay 与 Shower 合同
6. integration 与 facade 不保存 Context 或 platform facts
7. 旧 facade 的 object、`ActivityLifecycleCallbacks` 接口、4 个业务方法、7 个 callback
   和逐项委派
8. 13 个旧 FQCN 引用路径与 snapshot 精确一致
9. `KiyoriApplication` 继续唯一调用 `ActivityLifecycleManager.initialize(this)`，不直接注册
   第二 callback
10. `kiyori-platform` ownership 不放宽
11. current Activity identity、foreground 0→1/1→0、destroy count 和 callback 注册四条 JVM
    合同测试
12. ARCH044 正反向架构测试本身必须存在

反向 fixture 覆盖 platform 导入 Operit、integration 保存 Context/facts、旧 facade 重新持有
状态或注册 callback、旧 FQCN consumer 丢失和 JVM 边界断言删除。

### 分层验证

最窄验证：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05c_platform_lifecycle_accepts_fact_and_side_effect_split `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05c_platform_lifecycle_rejects_state_or_compatibility_drift

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.platform.lifecycle.KiyoriActivityLifecycleFactsTest `
  --no-daemon --console=plain
```

阶段封板继续执行完整 architecture、完整 Python、完整 JVM XML 统计、formal/fresh-clone
readiness、fresh full lint 与影响路径审计、working-tree Markdown、`git diff --check`、规定
Debug 构建和 APK 静态审计。真实 Android callback、前后台切换、keep-screen-on、麦克风前台、
plugin、External Chat、PlayerCrash、VirtualDisplay 与 Shower 设备行为继续单独标记
`verification_pending`，不能由 JVM 或 APK 静态检查替代。

### M-05C 封板证据

- ARCH044 failure-first 单一缺失错误成立，正反向 fixture `2/2`、真实 ARCH044 与完整
  architecture `phase=m03` 全部通过
- 新增 4 条 `KiyoriActivityLifecycleFactsTest`；完整 JVM XML 为
  `136 suites / 817 tests / 0 failures / 0 errors / 0 skipped`
- 生产编译后 `javap` 与实施前公开 ABI 逐项一致；13 个旧 FQCN 引用路径保持，callback
  注册与四类事实状态只位于 platform owner
- 完整 Python 为 `162/162`；formal/fresh-clone readiness、working-tree Markdown
  `256 files / 0 issues`、`git diff --check` 与 lint baseline normalization 通过
- fresh full lint 保持既有 `27 errors / 287 warnings / 2 hints`，过滤基线仍为
  `1268 errors / 4373 warnings / 150 hints`；platform、integration、facade 与 facts test
  四条 M-05C 路径均为 0 命中
- 规定的 `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 稳定为
  `233 actionable tasks / 24 executed / 209 up-to-date`，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `477957302` bytes，SHA-256
  `3792DD58C87D1DDD4F977BB8CD4B4407458EB911EC17CB0CB48CB8876184D2D3`
- APK 为 `com.kiyori`、`45 / 0.1.0`、label `Kiyori`、SDK `26 / 34 / 36`；Application 为
  `com.kiyori.app.KiyoriApplication`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，`:crash / :player / :repair` 保持
- APK 只含 `arm64-v8a`，共有 53 个 native library、0 个重复 basename；10 个 player
  target library 各存在 1 份，Debug v2 signing 与 `zipalign -c -P 16 -v 4` 通过

未提交、未推送、未部署、未操作 terminal、设备、模拟器或 ADB。真实 Android callback、
前后台、keep-screen-on、plugin/AI/Player/VirtualDisplay/Shower 设备行为继续为
`verification_pending`。M-05C 本地封板完成，下一独立切片为 M-05D Android permission
capability。

## M-05D 精确实施清单

### 目标与非目标

M-05D 只迁移 `MainActivity` 启动阶段的 `POST_NOTIFICATIONS` Android capability，不建立
全项目权限框架。完成后：

- `com.kiyori.platform.permission.KiyoriNotificationPermissionCapability` 唯一拥有
  API 33 guard、system grant/rationale 读取、`RequestPermission` launcher 与请求执行
- `com.kiyori.integration.operit.permission.OperitNotificationPermissionResources`
  唯一桥接现有 denied/rationale 两个 `R.string` ID，不复制字符串值
- `KiyoriMainNotificationPermissionCoordinator` 只保留原日志与 Toast 投影，并继续由
  `MainActivity` 直接字段构造
- Android 系统 grant/rationale 是唯一运行时事实；不保存第二 boolean、Flow、DataStore
  或 SharedPreferences

明确非目标：

- 不迁移 `AndroidPermissionPreferences`、`AndroidPermissionLevel`、root execution mode、
  `su` command 或启动权限引导
- 不迁移 Browser download/userscript 的通知能力检查
- 不迁移相机、定位、麦克风、蓝牙、WebSession 或其他页面的权限请求
- 不改 Manifest 权限、资源文案、MainActivity 调用时机、namespace、协议、数据或 UI
- 不新增 fallback、动态 resource lookup、第二权限状态或万能 permission registry

### 实施前兼容面

源码实测只有 `MainActivity` 构造并调用
`KiyoriMainNotificationPermissionCoordinator`。实施前 `javap -public` 为：

```text
KiyoriMainNotificationPermissionCoordinator(ComponentActivity)
checkAndRequest()
```

这两项公开 JVM 面实施后逐项保持。旧
`KiyoriMainNotificationPermissionAction` 与
`resolveKiyoriMainNotificationPermissionAction` 虽会编译为 public bytecode，但源码为
`internal`，仓库内只有旧 4 条策略测试使用；没有 Manifest、反射、JavaScript、示例或协议
引用。因此二者随唯一事实 owner 迁入 platform，旧声明和旧测试路径删除，不保留双 owner
或 facade。

### 唯一 owner

```text
app/src/main/java/
├── com/kiyori/platform/permission/
│   └── KiyoriNotificationPermissionCapability.kt
├── com/kiyori/integration/operit/permission/
│   └── OperitNotificationPermissionResources.kt
└── com/kiyori/app/startup/
    └── KiyoriMainNotificationPermissionCoordinator.kt
```

platform source 不导入任何项目代码，只依赖 Android/AndroidX。它在构造时注册唯一 launcher；
`checkAndRequest` 在 API 33 以下先投影 `NOT_REQUIRED`，API 33+ 则每次重新读取系统
grant/rationale，先同步投影 action，再在 rationale Toast 返回后发起系统请求。它不缓存
`isGranted`、`shouldShowRationale` 或 callback 结果。

Operit resource bridge 只暴露两个 `@StringRes Int`，使 AI 回复相关文案继续属于现有 Operit
资源；platform 不感知 AI/Toast/资源，app coordinator 不再直接 import
`com.ai.assistance.operit.R`。因此
`KiyoriMainNotificationPermissionCoordinator.kt` 的到期 ARCH004 exception 删除，且不放宽
`kiyori-app` 或 `kiyori-platform` ownership。

### 行为与范围合同

必须保持以下顺序和结果：

1. coordinator 仍在 `MainActivity` 字段初始化期间构造，platform launcher 仍在
   `onCreate` 前注册
2. `performInitialChecks()` 仍只调用一次 `checkAndRequest()`
3. API < 33：记录原“无需请求”日志，不发起请求
4. 已授权：记录原“已授予”日志，不发起请求
5. 需要 rationale：先记录原日志并显示同一 long Toast，再启动系统权限框
6. 直接请求：先记录原日志，再启动系统权限框
7. result granted/denied 继续记录原日志；denied 继续显示同一 long Toast

Manifest 中 `android.permission.POST_NOTIFICATIONS` 保持一份。迁移后直接读取该权限的
Kotlin 路径必须精确为：

```text
app/src/main/java/com/kiyori/platform/permission/KiyoriNotificationPermissionCapability.kt
app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserDownloadRuntime.kt
app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/runtime/WebSessionUserscriptManager.kt
```

后两者继续只判断各自通知能力，不请求启动权限。`AndroidPermissionPreferences.kt`
LF-normalized SHA-256 必须保持
`DDCDD2A5208E7E4A9757B69A27074BF2F8249AA29BB7DF14B5A5AAEFC489BA45`。

### ARCH045 failure-first

ARCH045 在 production owner 出现前先由正反向 fixture 建立；真实工作树稳定只报告：

```text
ARCH045 M-05D notification permission capability missing:
app/src/main/java/com/kiyori/platform/permission/KiyoriNotificationPermissionCapability.kt
```

实现后 ARCH045 锁定：

1. platform、resource bridge、app coordinator 与未改 preference owner 的四份
   LF-normalized SHA-256
2. platform package、零项目依赖、唯一 action/resolver/capability 与精确 Android API 计数
3. platform 不拥有 Toast、资源、日志、DataStore、SharedPreferences、其他权限或业务状态
4. resource bridge 只映射两个现有 `R.string` ID，不拥有 runtime permission 或状态
5. coordinator 的四个精确项目 import、6 条日志、2 个 Toast、四 action 投影与零系统权限 API
6. MainActivity 继续原 FQCN import、原一参数构造与一次调用，不吸收 capability 或资源 mapping
7. 三个直接 `POST_NOTIFICATIONS` Kotlin consumer 与一份 Manifest 声明
8. 旧 app action/resolver/test 清零，新 platform 4 条策略测试存在
9. `AndroidPermissionPreferences` hash 不变且不被 platform/coordinator 引用
10. coordinator 的 ARCH004 资源 exception 清零，platform/integration ownership 不放宽
11. ARCH045 正反向架构测试本身必须存在

反向 fixture 覆盖 platform 导入 Operit/持有状态/吸收其他权限、resource bridge 持有 runtime
状态、coordinator 或 MainActivity 回流权限实现、直接消费者漂移、preference 漂移、测试断言
删除和到期 exception 回流。

### 分层验证

最窄验证：

```powershell
.\.venv\Scripts\python.exe -B -m unittest `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m04d_notification_permission_gate_accepts_early_registered_owner `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m04d_notification_permission_gate_rejects_drift_and_activity_owner `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05d_notification_permission_accepts_platform_and_resource_split `
  ci.test.test_architecture_boundaries.ArchitectureBoundaryTest.test_m05d_notification_permission_rejects_owner_scope_or_resource_drift

.\gradlew.bat :app:testDebugUnitTest `
  --tests com.kiyori.platform.permission.KiyoriNotificationPermissionCapabilityTest `
  --no-daemon --console=plain
```

failure-first、4 条正反向 architecture fixture、真实 ARCH036/ARCH045、完整
`phase=m03` architecture、生产 Kotlin 编译、4 条 platform policy JVM test 与 coordinator
实施前后精确公开 ABI 均通过。完整 Python 为 `164/164`，完整 JVM XML 为
`136 suites / 817 tests / 0 failures / 0 errors / 0 skipped`；formal/fresh-clone
readiness、256 个 working-tree Markdown、`git diff --check`、lint baseline normalization
与敏感签名扫描通过。

fresh full lint 保持仓库既有 `27 errors / 287 warnings / 2 hints`，baseline 另过滤
`1268 errors / 4373 warnings / 150 hints`，四条 M-05D source/test 路径均为 0 命中，未修改
或扩大 baseline。预封板规定 Debug 构建为
`233 actionable tasks / 28 executed / 205 up-to-date`，末尾
`:app:verifyDebugPlayerRuntimePackaging` 通过。`app-debug.apk` 为 `477957302` bytes，
SHA-256 `BB370BC2602880CA4DCE488F67DA4AB9F105C1CFF07A0E4D2FF31A3D3CC38B34`；
package/version/SDK/Application/唯一 launcher/`:crash/:player/:repair` 保持，APK 仅含
`arm64-v8a` 的 53 个 native library、0 个重复 basename，10 个 player target library 各
1 份，Debug v2 signing、单 signer 与 `zipalign -c -P 16 -v 4` 通过。

未提交、未推送、未部署、未操作 terminal、设备、模拟器或 ADB。真实 Android 13+
rationale、系统权限框、授权/拒绝和通知到达继续为 `verification_pending`，不能由 JVM 或
APK 静态检查替代。M-05D 本地封板完成，下一独立切片为 M-05E paths/storage。

## M-05E 精确实施清单

### 目标、非目标与稳定合同

M-05E 只收口 Kiyori 路径计算与备份目录命名所有权。完成后的唯一真实计算 owner 是：

```text
app/src/main/java/com/kiyori/platform/storage/KiyoriPaths.kt
```

必须逐值保持：

- public `Download/Kiyori` 根、内部 `filesDir` / `cacheDir` 路径和全部目录大小写、相对层级
- `ensureDir` 创建并返回同一目录的语义
- userscript、Browser 下载、导出、日志、插件配置、内存、模型配置、角色卡与工作目录入口
- plugin ID 的 trim、非法字符替换、空值命名和稳定 hash 后缀
- `backup/raw_snapshot/room_db/chat/memory/model_config/character_cards` 布局
- raw snapshot 排除集合、Room/manual/daily backup、旧布局扫描和备份文件格式

明确非目标：

- 不改 DataStore、SharedPreferences、数据库、ObjectBox、备份序列化、raw snapshot 格式
- 不改目录名、文件名、大小写、public Download 根、内部 sandbox 布局或旧布局扫描
- 不改 Browser/Player/Backup 业务流程、UI、Manifest、资源、依赖、namespace 或 Android 组件
- 不批量迁移全部 Operit AI/工具消费者，不建立 Browser/Player 第二路径 facade
- 不新增条件降级、第二目录计算、第二 `Environment` 调用、第二 `ensureDir` 或第二 plugin ID 算法
- 不提交、不推送、不部署、不操作 terminal、设备、模拟器或 ADB

### 唯一 owner、领域投影与兼容 facade

```text
app/src/main/java/
├── com/kiyori/platform/storage/
│   ├── KiyoriPaths.kt
│   └── KiyoriBackupPaths.kt
├── com/ai/assistance/operit/util/
│   └── OperitPaths.kt
└── com/ai/assistance/operit/data/backup/
    └── OperitBackupDirs.kt
```

- `KiyoriPaths` 唯一声明全部目录字符串、`Environment` 读取、`ensureDir` 与 plugin ID 计算
- `KiyoriBackupPaths` 是无状态、无目录字面量、无 `ensureDir` 的备份领域命名投影，只委派
  `KiyoriPaths`
- 旧 `OperitPaths` 保留原 object、五个 public 常量和全部方法 JVM ABI，只逐项委派
  `KiyoriPaths`
- 旧 `OperitBackupDirs` 保留原 object 和全部方法 JVM ABI，只逐项委派
  `KiyoriBackupPaths`
- 两个旧 facade 不 import `Environment`，不声明 `Regex`、目录字面量、排除集合或第二计算

实施前后 `javap -public` 已逐项比对。旧 `OperitPaths` 继续暴露 `INSTANCE`、五个 public
String 常量和原 35 个方法；旧 `OperitBackupDirs` 继续暴露 `INSTANCE` 和原 8 个方法。
该兼容面用于源码、Java/Kotlin 调用和上游补丁局部性，不授权恢复第二 owner。

### 精确消费者迁移

直接使用 `KiyoriPaths` 的生产路径精确为 9 个：

```text
app/src/main/java/com/ai/assistance/operit/util/OperitPaths.kt
app/src/main/java/com/kiyori/platform/storage/KiyoriBackupPaths.kt
app/src/main/java/com/ai/assistance/operit/util/AppLogger.kt
app/src/main/java/com/kiyori/app/KiyoriApplication.kt
app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserDownloadSupport.kt
app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/userscript/storage/UserscriptJsonStore.kt
app/src/main/java/com/ai/assistance/operit/ui/features/websession/browser/UserscriptLogExportHelper.kt
app/src/main/java/com/ai/assistance/operit/ui/features/player/PlayerLogExportHelper.kt
app/src/main/java/com/ai/assistance/operit/data/backup/RawSnapshotBackupManager.kt
```

直接使用 `KiyoriBackupPaths` 的生产路径精确为 7 个：

```text
app/src/main/java/com/ai/assistance/operit/data/backup/OperitBackupDirs.kt
app/src/main/java/com/ai/assistance/operit/data/backup/RawSnapshotBackupManager.kt
app/src/main/java/com/ai/assistance/operit/data/backup/RoomDatabaseBackupManager.kt
app/src/main/java/com/ai/assistance/operit/data/backup/RoomDatabaseRestoreManager.kt
app/src/main/java/com/ai/assistance/operit/data/preferences/CharacterCardManager.kt
app/src/main/java/com/ai/assistance/operit/data/repository/ChatHistoryManager.kt
app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ChatBackupSettingsScreen.kt
```

其余 37 个生产文件继续调用旧 `OperitPaths` facade。它们属于 Operit AI、工具、provider、
service 或仍未进入领域迁移的 UI 路径；本切片不以批量 import 改写制造上游冲突。
`OperitBackupDirs` 的外部生产消费者已清零，只有定义文件自身保留兼容入口。

三组集合分别由以下 snapshot 精确锁定：

```text
config/architecture/m05e-direct-kiyori-path-consumers.txt
config/architecture/m05e-direct-kiyori-backup-path-consumers.txt
config/architecture/m05e-legacy-operit-path-consumers.txt
```

### 合同测试与 ARCH046

新增 `KiyoriPathsTest`，以 JVM 可控制的根目录和纯 helper 锁定：

1. `/sdcard/Download/Kiyori` 下的 public 目录精确值
2. raw snapshot 排除集合
3. plugin ID 原值、非法字符、空值与 hash 后缀
4. backup、raw snapshot、room、chat、memory、model config 与 character-card 层级
5. files/cache pool 和临时目录层级

ARCH046 failure-first 在 production owner 出现前，真实工作树只报告：

```text
ARCH046 M-05E Kiyori paths owner missing:
app/src/main/java/com/kiyori/platform/storage/KiyoriPaths.kt
```

实现后 ARCH046 锁定：

1. 四个 production owner/facade 的 LF-normalized SHA-256
2. `KiyoriPaths` 的 package、零项目依赖、目录字面量、公开常量、唯一
   `Environment` / `ensureDir` / plugin ID 算法和 raw snapshot 排除集合
3. `KiyoriBackupPaths` 的纯委派、零目录字面量与零创建逻辑
4. 两个旧 facade 的完整 object/API/常量形状、逐项委派和计算状态清零
5. 9 个 direct path consumer、7 个 direct backup consumer、37 个 legacy path consumer
   与 0 个 legacy backup external consumer
6. M-03 Application、M-05B logging 和三个 critical backup source 的同步 snapshot
7. 五条 `KiyoriPathsTest` 合同断言和 ARCH046 正反向架构测试本身

反向 fixture 拒绝 platform owner 导入项目代码、facade/projection 恢复路径计算或字面量、
direct/legacy consumer 集合漂移、旧 backup consumer 回流和合同测试断言删除。

### 当前定向证据与封板门禁

- ARCH046 failure-first 单一缺失错误成立
- M-05B/M-05E 正反向架构 fixture `4/4` 通过
- 真实 M-03、M-05B、M-05E 与 critical file hash 门禁通过
- `KiyoriPathsTest` 为 `5 tests / 0 failures / 0 errors / 0 skipped`
- 生产 Kotlin 编译通过；旧两个 facade 的公开 JVM ABI 与实施前逐项一致
- direct/legacy consumer 集合实测为 `9 / 7 / 37 / 0`
- `git diff --check` 当前通过，暂存区保持为空

### M-05E 封板证据

- 完整 architecture `phase=m03` 在 baseline location 迁移前后两次通过，最终结果为
  `errors=[]`
- 完整 Python 为 `166/166`；完整 JVM XML 为
  `137 suites / 822 tests / 0 failures / 0 errors / 0 skipped`
- formal readiness 与 fresh-clone readiness 通过；working-tree Markdown 为
  `256 files / 0 issues`，`git diff --check` 通过
- fresh full lint 恢复仓库既有 `27 errors / 287 warnings / 2 hints`，baseline 另过滤
  `1268 errors / 4373 warnings / 150 hints`
- 17 条 M-05E Kotlin 影响路径只有
  `BrowserDownloadSupport.kt:3162` 的既有 `UseKtx` warning；迁移前同一路径已有该问题，
  新 owner、projection、两个 facade 与合同测试均为 0 current-only 命中
- 唯一既有 `SdCardPath` baseline 记录只把 location 从旧 `OperitPaths.kt` 迁到
  `KiyoriPaths.kt`，未新增、删除或吸收 issue；baseline 仍为 5791 条，归一化 SHA-256 为
  `b51d9da65832ff45b0e9b652d2a6632d0da380dc427a37988e5b90b9861b06bf`
- changed/untracked 允许清单共扫描 250 个文本文件，密钥、令牌、私钥和赋值型 secret
  签名为 0；122 个 untracked 路径中无 `.env`、keystore、private key 或 credentials 命名
- 预封板规定构建
  `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
  为 `BUILD SUCCESSFUL in 48s`、`233 actionable / 28 executed / 205 up-to-date`，
  `verifyDebugPlayerRuntimePackaging` 实际执行
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `477957302` bytes，SHA-256
  `E6A5E78CFB4441399DC36D83583BF6F441441E3736A85A17759695D73ACF790C`
- APK 为 `com.kiyori`、`45 / 0.1.0`、label `Kiyori`、SDK `26 / 34 / 36`；Application 为
  `com.kiyori.app.KiyoriApplication`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，`:crash / :repair / :player` 保持
- APK 只含 `arm64-v8a`，共有 53 个 native library、0 个重复 basename；10 个 player
  target library 各存在 1 份，44 个 DEX、Debug v2、单 signer 与
  `zipalign -c -P 16 -v 4` 通过
- 主仓保持 `main@176f803e683307aa8e182fb357f35f84755c5f8b`，
  `origin/main@62464b054f6de00b70c5596295bc216eb8edf63d`，ahead 14/behind 0；
  暂存区为空，`terminal` gitlink 与未初始化 hotbuild 子模块状态未漂移

未提交、未推送、未部署、未操作 terminal、设备、模拟器或 ADB。真实 public/internal
目录读写、Browser/Player 导出、备份/恢复、raw snapshot、旧布局扫描和权限交互继续为
`verification_pending`，不能由 JVM、lint 或 APK 静态检查替代。M-05E 已完成本地封板，
M-05 design/theme/platform 阶段完成；后续 Browser 产品域必须另立里程碑和设备验收。
