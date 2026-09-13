# 实施顺序、反向检查与验收

## 第一阶段：主题 owner

1. 简化 `ThemeColorSchemeResolver`，只按浅深模式返回固定 Kiyori ColorScheme
2. 简化 `OperitTheme` 与 `OperitUtilityTheme`，删除旧颜色和根背景消费
3. 固定 `AppContent` AppBar 的容器、内容和分隔线颜色
4. 把 `AppBackgroundLayer` 挂入 `AIChatScreen`
5. 收紧 `ThemePreferenceSnapshot`、角色主题键集合和 WebChat schema

反向检查：

- `useCustomColors`、`customPrimaryColor`、`customSecondaryColor` 和 `onColorMode` 在主源码为零
- AppBar 自定义色、工具栏透明和强制前景色在主源码为零
- `OperitTheme` 不引用背景媒体 preference 或 ExoPlayer
- 角色主题键集合不含应用模式、全局字体、状态栏或 AppBar

## 第二阶段：设置视觉

1. 新增 `KiyoriSettingsTheme`、`KiyoriSettingsColors` 和全应用 `KiyoriSemanticTone`
2. 重构折叠页面、分组、设置行、开关、分隔线和选择面板
3. 让设置首页和拆分根页使用图标容器与语义 tone
4. 给主题页建立固定应用主题与 AI 局部个性化的清晰分区
5. 用设置主题包裹所有 `NavItem.Settings` 子页面

反向检查：

- `KiyoriSettingsUi`、`KiyoriCollapsingSettingsPage` 和 Settings Home 不含浅色专用背景、文本和
  分隔线常量
- 小程序管理仍为 `NONE`
- 设置根与子页仍使用原导航和原状态 owner

## 第三阶段：验证

按风险从窄到宽执行：

1. `KiyoriThemeTest`
2. `KiyoriSettingsPagesTest`
3. 与角色主题快照、WebChat schema 或 AppContent 相关的既有 JVM 测试
4. `:app:compileDebugKotlin`
5. `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
6. `git diff --check`
7. `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
8. 核验 APK 路径、时间、大小、SHA-256、包名、版本、签名和 `zipalign -P 16`

本地构建不能证明真实设备的颜色、对比度、系统主题切换、输入法、长表单、视频背景生命周期或
角色切换视觉。未安装目标 APK 前，设备验收保持 `verification_pending`。

## 本地实施结果

- [DONE] 主源码中的旧全局颜色、AppBar 自定义色、透明工具栏与强制前景色标识为零；旧字符串
  资源和 `ThemeSettingsColorSection` 引用为零
- [DONE] `OperitTheme` 只处理固定应用浅深模式、状态栏可见性与全局字体；AI 背景只在
  `AIChatScreen` 渲染
- [DONE] `KiyoriSettingsTheme` 覆盖设置宿主的顶栏、背景、加载态和正文，并提供七种语义图标
  tone；该 tone 已提升为全应用 `KiyoriSemanticTone`，设置首页和拆分设置根继续使用原导航与
  原状态 owner
- [DONE] 角色主题键不含应用模式、全局字体、状态栏或 AppBar；WebChat 根快照不含旧全局颜色
  字段，固定解析后的 primary/secondary 只存在于 `palette`
- [DONE] `KiyoriThemeTest`、`KiyoriSettingsPagesTest`、`KiyoriShellStateTest` 与
  `WebThemeSnapshotSchemaTest` 合计 `64/64`，零失败、零错误、零跳过
- [DONE] `:app:compileDebugKotlin`、formal readiness 与 `git diff --check` 通过
- [DONE] `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 11s`，233 个任务零失败；
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- [DONE] Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `474822245` 字节，
  SHA-256 `132BA8AAB029499F8868F6C58B936503DE3F80BEC1B775965926E2C86512832C`
- [DONE] APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、`arm64-v8a`；Android Debug
  V2 签名和 `zipalign -c -P 16 4` 通过
- [NOTE] 构建机文件系统时间显示 `2026-07-30 02:49:11 +08:00`，晚于当前日期
  `2026-07-29`，属于本机时钟偏差，不作为项目日期
- [ ] 未安装 APK、未操作设备；浅色、深色、系统模式、长语音表单、底部面板和 AI 局部外观
  保持 `verification_pending`

## 2026-07-30 Shell 设置子页主题边界修复

Crash Report `92a847df-518a-477d-b3cb-870b17273550` 的主线程栈定位到
`KiyoriDownloadSettingsPage` 打开 `KiyoriSettingsSelectionSheet` 时读取
`LocalKiyoriSettingsColors` 失败。

根因不是颜色缺省值，而是 Compose 提供范围错误：

- `KiyoriCollapsingSettingsPage` 在内部提供 `KiyoriSettingsTheme`
- 下载器和播放器的选择面板在折叠页面调用结束后作为同级节点渲染
- 同级选择面板不属于折叠页面内部主题子树，因此首次显示即触发严格异常

修复与门禁：

- [DONE] `KiyoriAppShell` 在完整浏览器、下载器和播放器设置子页外提供
  `KiyoriSettingsTheme`，使列表、选择面板和后续同级覆盖层共享同一主题 owner
- [DONE] 全屏网页搜索继续只使用 `KiyoriBrowserTheme`
- [DONE] `LocalKiyoriSettingsColors` 继续在缺少 owner 时抛出异常，不增加默认色或回退路径
- [DONE] `KiyoriShellStateTest` 覆盖全部 Shell 设置子页与非设置子页的主题映射
- [DONE] `KiyoriShellStateTest` 与 `KiyoriSettingsPagesTest` 合计 `56/56`，零失败、零错误、
  零跳过；测试任务完成 `:app:compileDebugKotlin`
- [DONE] formal readiness 与 `git diff --check` 通过
- [DONE] `:app:assembleDebug` 通过，233 个任务零失败；
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- [DONE] Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-30 12:06:51 +08:00`，大小 `482615791` 字节，SHA-256
  `E3F7D90A72851CBBFB4479A313CD8D0BE50F2B1346D5DD1D4CEBA09835DA721E`
- [DONE] APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、`arm64-v8a`；
  Android Debug V2 签名和 `zipalign -c -P 16 -v 4` 通过
- [PENDING] 报告设备上的下载器与播放器选择面板复测
