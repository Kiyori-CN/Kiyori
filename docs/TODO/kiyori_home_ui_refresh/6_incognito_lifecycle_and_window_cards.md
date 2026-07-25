# 6. 无痕生命周期与窗口卡片

状态：自动验证与 Debug APK [DONE]；真机验收 [PENDING]

## 现状与根因

- 当前无痕窗口固定绑定 `kiyori-incognito-session-v1`，最后一个 WebView 销毁后立即调用 `ProfileStore.deleteProfile()`。
- AndroidX WebKit 禁止在同一进程删除已经通过 `getOrCreateProfile()` 或 `getProfile()` 加载的 Profile。WebView 销毁不会解除该限制，因此旧逻辑必然把运行状态标记为 `PROFILE_RESET_FAILED`。
- 软件首页搜索、浏览器搜索、网页访问记录和标题更新均未按 Profile 过滤，无痕活动会写入普通历史存储。
- 窗口缩略图固定为横向 `320x180`，捕获时只按宽度缩放；竖屏网页的下半部分被裁掉，窗口卡片也无法呈现完整的当前可见视口。

## Profile 生命周期合同

- 每段无痕会话使用唯一的 `kiyori-incognito-<代际 ID>` Profile；同一时间存活的全部无痕窗口共享该代际。
- 最后一个无痕窗口关闭后，先销毁 WebView，再清理该 Profile 的 Cookie、WebStorage 和定位授权，并立即退休代际。随后创建的无痕窗口必须使用新名称，不能读取退休代际的缓存或站点数据。
- AndroidX 不允许在当前进程物理删除已加载 Profile。退休代际在下次应用冷启动、任何 Kiyori 无痕 Profile 加载前统一删除。
- 启动清理以重新查询后的 Profile 列表为准；`deleteProfile()` 返回 `false` 只表示目标已不存在，不能单独判定为清理失败。
- 无痕能力不支持或启动清理确实失败时，拒绝创建无痕窗口，不创建普通窗口替代，也不静默修改默认模式。

## 持久化边界

- 普通窗口继续写入搜索历史、浏览历史和页面标题。
- 无痕窗口不调用 `addSearchHistory()`、`recordVisit()` 或 `updateTitle()`；标题更新也必须拦截，避免修改普通历史中的同 URL 记录。
- Cookie、缓存、WebStorage、Service Worker 等网站数据由唯一代际 Profile 隔离；退休代际不再复用，并在下次冷启动物理删除。
- 书签和下载是用户明确发起的持久化动作，不因无痕模式静默取消或删除。Kiyori AI 已获授权的浏览器操作仍可控制当前无痕窗口。

## 搜索页视觉合同

- 普通模式使用睁眼图标，无痕模式使用闭眼图标；按钮使用标准无背景 `IconButton`，不绘制边框、底色或阴影。
- 切换提示固定为“已开启无痕模式”和“已关闭无痕模式”，白底黑字，使用 `bodyMedium`，水平 `16dp`、垂直 `9dp` 内边距。
- 提示位于导航栏安全区上方 `32dp`，保留约 `1.2s` 自动消失。

## 窗口卡片合同

- 缩略图尺寸改为 `320x512`，预览比例固定为 `5:8`。
- 捕获使用宽高两个方向的最小缩放值，并把完整当前 WebView 视口居中绘制到 Bitmap；不得裁切网页内容。
- 卡片使用纵向圆角细边框，活动窗口使用强调色 `2dp` 边框，其他窗口使用 `1dp` 边框。
- 图片使用 `ContentScale.Fit`；卡片保留窗口编号、关闭按钮、标题和 URL，删除分组内重复的 Profile 文案。
- 手机、平板和横屏继续使用现有 `2/3/4` 列断点，不创建另一套窗口状态或缩略图缓存。

## 验收

- JVM 测试覆盖普通/无痕历史策略、同一代际共享、退休后换代、目标已不存在时启动清理成功，以及竖向/横向视口完整适配。
- `git diff --check`、正式开发准备门禁、定向 JVM 测试、显式 Kotlin 编译和 Debug APK 构建通过。
- 核验 APK 时间、大小、SHA-256、包名、版本、Debug V2 签名和 16 KB ZIP 对齐。
- 真机确认普通与无痕 Cookie 隔离、关闭最后一窗后新代际无登录状态、重启后旧代际清理、历史不落盘，以及手机和平板窗口卡片视觉。

## 自动验证结果

- [PASS] `git diff --check`。
- [PASS] `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`。
- [PASS] 定向 `:app:testDebugUnitTest`：`33` 个测试，零失败、零错误、零跳过；覆盖 `WebSessionProfilePolicyTest`、`BrowserThumbnailLayoutTest`、`WebSessionBrowserChromeLayoutTest`、`KiyoriSoftwareHomeSearchTest`、`PendingAiHomeActionHandlerTest` 和 `KiyoriWeatherModelsTest`。
- [PASS] `:app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL in 39s`。
- [PASS] `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 3m 39s`，`230 actionable tasks`。
- [PASS] `app/build/outputs/apk/debug/app-debug.apk`：`2026-07-25 19:31:48 +08:00`，`449498358` bytes，SHA-256 `A424411508DD06B99A4593A8C323A30EBAF5E8E0C3F722A8B86D46F7A168F44F`。
- [PASS] APK 元数据：`com.kiyori`，`versionCode=45`，`versionName=0.1.0`，`minSdk=26`，`targetSdk=34`，应用标签 `Kiyori`。
- [PASS] `apksigner verify --verbose --print-certs`：Android Debug 证书，APK Signature Scheme v2。
- [PASS] `zipalign -c -P 16 -v 4`：16 KB ZIP 对齐通过。
- [PENDING] 真机验证站点数据隔离、进程内换代、冷启动物理删除、历史不落盘，以及手机和平板窗口缩略图与提示视觉。
