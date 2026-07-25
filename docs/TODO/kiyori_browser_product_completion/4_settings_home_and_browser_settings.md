# 设置首页与浏览器设置（已被后续决策取代）

## 后续决策

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
