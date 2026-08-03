# 网页浏览器设置复刻

## 2026-08-03 自动悬浮最小时长

[LOCAL DONE]

- 当前五组实际为 `6/7/5/6/6`：“网页插件与脚本 / 主页、标签与手势 / 音视频嗅探 /
  网站权限与数据 / 显示与高级”。
- `WebSessionBrowserSettingsStore` 新增唯一
  `automaticFloatingMinimumDurationMillis`，默认 `60_000ms`；预设为
  `30 秒 / 1 / 3 / 5 / 10 / 30 / 60 分钟`，自定义输入接受 `1..86400` 整秒。
- “自动悬浮最小时长”位于“自动悬浮播放”之后，自动悬浮关闭时显示为依赖禁用；人工播放不读取
  该阈值。
- 选择面板继续复用 `KiyoriSettingsSelectionSheet`，自定义值使用明确的数字输入弹窗；设置页不创建
  第二份状态或媒体选择逻辑。
- 聚焦 `KiyoriSettingsPagesTest 11/11` 与
  `KiyoriBrowserPluginSettingsPolicyTest 2/2` 已通过；最终 formal readiness、Debug APK 和真机视觉
  证据以 `docs/TODO/README.md` 的本轮记录为准。

## 2026-07-29 播放器标准统一

[LOCAL DONE]

本节后续历史保留旧版复刻过程；其中行数和真实设置 owner 已由上方 2026-08-03 记录替代：

- 五组按 `3/5/4/5/6` 固定为“插件与会话 / 主页、标签与手势 / 音视频嗅探 /
  网站权限与数据 / 显示与高级”
- 分组标题、说明、双行设置项、Material Switch、`42%` 禁用态和选择面板全部复用
  `KiyoriSettingsUi`
- `WebSessionBrowserSettingsStore` 继续唯一持有主页、搜索栏嗅探入口、自动悬浮播放、网页打开应用
  和网页定位五项真实状态
- “搜索栏嗅探入口”和“自动悬浮播放”集中在“音视频嗅探”组；媒体候选抽屉删除设置卡，只保留
  格式筛选、候选列表以及播放、下载、复制和查看链接操作
- 尚无本设置页 runtime consumer 的项目保留产品信息，但使用明确的不可点击禁用态，不再进入
  同标题空页面
- 主页自定义子页也使用同一折叠标题、分组说明和设置行，输入弹窗只负责校验并写入既有主页 owner

本轮定向 `KiyoriSettingsPagesTest` `8/8` 与主源码 Kotlin 编译通过；最终 Debug APK 和真机视觉
验收以 `docs/TODO/README.md` 的 2026-07-29 本轮记录为准。

## 2026-07-26 当前里程碑

[DONE]

严格参考 `D:\10_Project\kiyori-android@24a2dfa9` 的 `SettingsBrowserScreen.kt`、`SettingsScreen.kt`、`BrowserPreferencesRepository.kt` 和运行时消费者，恢复旧版五张卡片的 `5/5/2/5/6` 顺序、固定浅色页面、行高、文字、右箭头和方形勾选框。设置首页“网页浏览器”和浏览器菜单第四行设置按钮打开同一个 `KiyoriShellChild.BROWSER_SETTINGS`。

### 真实消费者矩阵

| 旧版项目 | Kiyori owner | 当前作用 |
| --- | --- | --- |
| 自动悬浮播放 | `WebSessionBrowserSettingsStore` | 只控制推荐视频候选是否自动进入现有悬浮播放器；不改变人工点击结果的横向全屏语义 |
| 网页主页自定义 | `WebSessionBrowserSettingsStore` + `StandardBrowserSessionTools` | 约束浏览器主页按钮、人工新窗口和 AI `browser_tabs create` |
| 允许网页打开应用 | `WebSessionBrowserSettingsStore` + WebView navigation override | 关闭后直接拒绝外部 scheme，不创建外部打开确认请求 |
| 允许网页获取位置 | `WebSessionBrowserSettingsStore` + WebChromeClient geolocation request | 关闭后直接拒绝网页定位；开启后继续使用现有 Android 权限协调器 |
| 腾讯 X5 调试 | 空占位 | 旧版 X5 有真实消费者；当前 Kiyori 固定使用 Android System WebView，不引入第二个 WebView runtime |
| 其余旧版行 | 空占位 | 导航行打开同标题空页；旧版无消费者的勾选项保持旧显示值但不可交互 |

### 页面与返回合同

- Settings Home 打开时保留 `SETTINGS_HOME` owner，Back 返回原设置首页
- Browser Home 打开时保留 `BROWSER_HOME`、活动 WebView 和 WebSession，Back 恢复原网页且不导航、不重载
- overlay 打开时先收缩 focusable overlay，再使用 `com.kiyori.action.OPEN_BROWSER_SETTINGS` 请求同一 Shell child；媒体网页仍留在原 Browser Runtime
- 主页编辑子页和空占位子页在设置页面内部持有，系统 Back 与顶栏返回先关闭子页，再关闭 Browser Settings

### 2026-07-26 设置子页面视觉切片

- 浏览器设置根标题改为“网页浏览器设置”，与文件下载器设置共用 `KiyoriCollapsingSettingsPage`
- 顶栏返回键和状态栏 inset 固定；同一个标题在列表前 `72dp` 滚动内，从展开态 `start=32dp / top=60dp / 26sp` 连续插值到吸顶态 `start=56dp / top=16dp / 20sp`
- 顶栏内容高度同步从 `128dp` 收缩到 `56dp`；进度到达 `1` 后固定，继续滚动不再改变标题位置、字号或顶栏高度
- 五组浏览器设置卡与主页自定义卡均移除最外围描边，内部 `0.6dp` 分隔线、行高、右箭头和方形勾选框保持不变
- 手机、平板、横屏和显示挖孔使用实际 `WindowInsets.statusBars`；标题单行完整显示，不使用两份标题交叉淡入

### 验收门禁

- `KiyoriSettingsPagesTest` 锁定五组顺序和四个真实接线能力
- `KiyoriShellStateTest` 锁定 Settings Home、Browser Home 和 overlay external child owner
- 运行正式开发准备门禁、`git diff --check`、定向 JVM 测试和 `:app:assembleDebug`
- 核验 Debug APK；手机/平板视觉、真实网页外部 scheme、定位权限和 Browser Home 原页恢复保持 `verification_pending`

### 2026-07-26 本地验收结果

- `KiyoriSettingsPagesTest` 5 项与 `KiyoriShellStateTest` 29 项通过，零跳过、零失败、零错误
- `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- `git diff --check` 通过；仅有工作树既有 CRLF 到 LF 提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 在 `3m 21s` 完成 230 个任务，零失败
- Debug APK：`D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk`
- 生成时间：`2026-07-26 12:05:43 +08:00`
- 大小：`449485670` 字节
- SHA-256：`5A44006F4EE1AC3056D8F93BC42C04F74B6AACA87566207CC9B4033CB697A7E3`
- application ID：`com.kiyori`
- 版本：`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`
- 签名：Android Debug certificate，APK Signature Scheme v2 通过
- 对齐：`zipalign -c -P 16 4` 通过
- 手机/平板视觉、真实网页外部 scheme、定位权限、overlay Intent 拉起、WebView 保活和 Browser Home 返回仍为 `verification_pending`

### 2026-07-28 视频嗅探设置职责拆分

- 浏览器设置中的旧“悬浮嗅探播放”更名为“自动悬浮播放”，只持久化自动悬浮播放器开关
- 媒体候选抽屉顶部另设“搜索栏嗅探入口”，只控制搜索框右侧已发现视频资源球
- 两个设置由同一个 `WebSessionBrowserSettingsStore` 持有，但没有互相覆盖或并行状态 owner
- Kiyori 尚未发布，因此旧混合字段和 DataStore key 直接删除，不保留兼容别名或迁移分支

## 历史决策记录

本里程碑记录 2026-07-25 曾完成的浏览器设置实现与构建证据。2026-07-26 的[专业主题第七阶段](../kiyori_professional_browser_theme/7_ai_theme_settings_and_browser_settings_removal.md)已删除浏览器设置页、搜索记录管理子页、Shell 路由、外部 Intent 和页面专用逻辑。浏览器菜单第四行“浏览器设置”按钮、原图标和三按钮位置继续保留，点击为空；下文只作为历史记录，不再描述当前源码。

## 旧实现

Settings Home 只有一条 AI 设置入口，无法体现 Kiyori 作为浏览器产品的能力所有权。浏览器菜单中的“浏览器设置”仍是说明页。

## 设置首页

只显示已有真实状态 owner 或本里程碑同步实现的入口：

- 网页浏览器
- Operit AI 设置

下载中心和文件下载器设置在第五里程碑完成真实页面后加入；视频播放器在播放器基础里程碑完成后加入。当前只有 Shizuku 授权页与 AI 工具授权页，尚未形成 `CONTEXT.md` 定义的 Kiyori Permission Center，因此本里程碑不把任一旧页面伪装成权限中心。

不展示音乐、小说、广告拦截、备份等尚未建立 Kiyori 页面和状态 owner 的空入口。

## UI

- 顶部使用紧凑标题和说明，不复制旧参考页无功能的搜索、扫描、刷新和太阳按钮
- 入口按“浏览与内容”“AI 与系统”分组，使用 Operit theme 的圆角卡片、图标色和右箭头
- 手机为单列列表；840dp 及以上为两列分组卡片，内容最大宽度受限
- 页面与子设置使用同一个 Shell child stack；系统 Back 和顶栏返回都回到 Settings Home
- 从 Browser Home 打开时，浏览器设置覆盖在同一个 WebView presentation 上方，返回后直接恢复原网页，不重新挂载或导航
- 从 overlay 菜单打开时先收缩悬浮网页，再通过显式 Kiyori 内部 Intent 拉起同一 Browser Settings 页面

## 浏览器设置

首期只加入能直接约束现有 Browser Runtime 的设置：

- 默认搜索引擎，复用 `WebSessionHistoryStore`
- 新窗口默认 Profile，在 Multi-Profile 可用时选择普通或无痕
- 默认 UA 模式，复用现有 desktop/mobile 设置 owner
- 搜索记录管理入口，进入同一搜索记录数据
- 网站数据说明，显示普通与无痕生命周期，不提供尚未实现的批量清理按钮

浏览器菜单“浏览器设置”和 Settings Home 必须打开同一页面、同一 store，不复制状态。

## 已采用实现

- `KiyoriShellChild` 增加 `BROWSER_SETTINGS` 与 `BROWSER_SEARCH_HISTORY`，Back 从搜索记录返回浏览器设置，再返回原 Settings Home 或 Browser Home owner
- Settings Home 在 `<840dp` 使用单列分组卡片，在 `>=840dp` 使用两列；页面只展示“网页浏览器”和“Operit AI 设置”
- Browser Settings 直接读取 `WebSessionHistoryStore.searchEngineFlow`、`searchHistoryFlow`、既有 desktop mode 与 Browser Runtime 默认 Profile
- 搜索记录页复用同一 store，支持逐条删除和清空，不复制记录
- 浏览器菜单删除 `BROWSER_SETTINGS` 说明页；App presentation 直接打开 Shell child，overlay presentation 收缩后使用 `com.kiyori.action.OPEN_BROWSER_SETTINGS` 打开同一 child
- 网站数据区只解释普通与无痕生命周期，不提供未实现的批量清理按钮

## 自问自答

### 为什么播放器入口不能先放出来？

设置项代表真实功能 owner。播放器尚未建立会话、Activity 和持久化设置时展示入口只会形成空页面，违反本轮清理占位能力的目标。

### 浏览器设置是否应该包含所有 WebView setting？

不应该。只暴露用户能够理解且具备稳定产品语义的选项；调试、缓存细节和内部 WebView flags 不成为设置。

## 预计文件

- `KiyoriSettingsPages.kt`、`KiyoriShellPages.kt`、`KiyoriShellState.kt`、`KiyoriAppShell.kt`
- `BrowserPresentationCoordinator.kt` 作为既有 Browser Runtime 设置适配器
- `WebSessionBrowserHost.kt`、`WebSessionBrowserScreen.kt`、`KiyoriBrowserHome.kt` 与 `MainActivity.kt` 的菜单和 Shell 路由
- Shell navigation 与 settings store 测试
- `README.md`、`CONTEXT.md`

## 验收

- Settings Home 只包含真实入口，手机与平板布局稳定
- 浏览器设置从 Settings Home 和浏览器菜单进入同一页面并共享状态
- 改变引擎、默认 Profile 和 UA 后新窗口行为与 UI 同步
- Back 不丢失原 Settings Home 或 Browser Home 来源
- Debug APK、提交和推送门禁通过

## 本地验收结果

[DONE]

- `KiyoriShellStateTest` 与 `KiyoriSettingsPagesTest` 共 30 项 JVM 测试通过，零失败、零错误
- Debug Kotlin、资源合并与本轮新增七组语言资源编译通过；已删除零引用且缺少默认值的旧 `web_session_close_current_tab` 翻译资源
- `ci/script/check_formal_readiness.py --repository . --require-main` 与 `git diff --check` 通过
- `:app:assembleDebug` 完成 230 个任务，零失败
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 生成时间：`2026-07-25 01:03:31 +08:00`
- 大小：`449491326` 字节
- SHA-256：`1A25388BD8C82207229227CEF1548075B96E296281A7E561112BDF290C8FFCD8`
- application ID：`com.kiyori`
- 版本：`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`
- 签名：Android Debug certificate，APK Signature Scheme v2 通过
- 对齐：`zipalign -c -P 16 4` 通过
- Settings Home 手机/平板视觉、系统 Back、Browser Home 原页恢复和 overlay Intent 拉起仍为 `verification_pending`
