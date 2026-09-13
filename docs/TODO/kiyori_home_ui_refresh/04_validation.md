# 4. 验证

状态：上一版自动验证 [DONE]；本次 UI 细化自动验证与 Debug APK [DONE]；真机验收 [PENDING]

## 自动验证顺序

1. 纯逻辑/JVM 单测：WMO 映射、天气响应解析、布局档位、AI 动作单次消费、窗口命令。
2. `git diff --check`。
3. 正式开发准备门禁。
4. Debug 定向单测与 Kotlin 编译。
5. `:app:assembleDebug --no-daemon --console=plain`。
6. 核对 APK 时间、大小、SHA-256、包名、版本、Debug 签名和 ZIP 对齐。

## 真机待验证

- 定位权限首次请求、拒绝、再次授权和城市/温度正确性。
- 天气网页、真实窗口总览、附件、相机和全屏语音的端到端行为。
- 手机和平板四种方向的视觉密度、细渐变描边、IME、系统 Back 与无障碍内容描述。

自动检查与构建不能代替上述真机验收，最终状态保留为 `verification_pending`。

## 2026-07-25 首页黄金顶部线微调

- [UPDATED] 搜索框定位合同从“中线位于 `38.2%`”改为“顶部描边位于 `38.2%`”，标题和搜索框随同一测量布局整体下移。
- [UPDATED] 标题圆环为 `18dp`，搜索框常规/短屏高度为 `114/96dp`，Compact/Medium/Expanded 最大宽度为 `544/584/624dp`，最小水平留白为 `24/48/72dp`。
- [PASS] `git diff --check` 和 `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`。
- [PASS] 定向 `:app:testDebugUnitTest`：`WebSessionProfilePolicyTest` 13 个、`BrowserThumbnailLayoutTest` 3 个、`WebSessionBrowserChromeLayoutTest` 3 个、`KiyoriSoftwareHomeSearchTest` 8 个、`PendingAiHomeActionHandlerTest` 2 个、`KiyoriWeatherModelsTest` 4 个，共 33 个测试，零失败、零错误、零跳过；`BUILD SUCCESSFUL in 3m 26s`，`155 actionable tasks`，其中 `8 executed`。
- [PASS] `:app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL in 37s`，`79 actionable tasks` 均为最新状态。
- [PASS] `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 1m 43s`，`230 actionable tasks`，其中 `25 executed`。
- [PASS] `app/build/outputs/apk/debug/app-debug.apk`：`2026-07-25 21:31:27 +08:00`，`449498358` bytes，SHA-256 `2B7EB9CF7C563017BEF45261FCF6188B0ACF1DFD1406F06F47A8F0A5C79BA660`。
- [PASS] APK 元数据：`com.kiyori`，`versionCode=45`，`versionName=0.1.0`，`minSdk=26`，`targetSdk=34`，应用标签 `Kiyori`。
- [PASS] `apksigner verify --verbose --print-certs`：Android Debug 证书，APK Signature Scheme v2。
- [PASS] `zipalign -c -P 16 4`：16 KB ZIP 对齐通过。
- [PENDING] 手机和平板确认黄金顶部线、标题图标、搜索框高度和三档水平留白。

## 2026-07-25 自动验证结果

- [PASS] `git diff --check`
- [PASS] `.venv\\Scripts\\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
- [PASS] `:app:compileDebugKotlin --no-daemon --console=plain`
- [PASS] `:app:testDebugUnitTest` 定向执行 `KiyoriWeatherModelsTest`、`PendingAiHomeActionHandlerTest` 和 `KiyoriSoftwareHomeSearchTest`；该次构建中的首页测试覆盖 `439dp/440dp` 高度边界、`-0.236` 纵向偏置、宽度档位和 Search/AI 主点击目标映射
- [PASS] `:app:assembleDebug --no-daemon --console=plain`
- [PASS] `app/build/outputs/apk/debug/app-debug.apk`：`2026-07-25 16:10:49 +08:00`，`449495558` bytes，SHA-256 `B7D9F8F226882722DB02419CB72FFE11FE09BCC816AF78CBA480B8BACDCD79A6`
- [PASS] APK 元数据：`com.kiyori`，`versionCode=45`，`versionName=0.1.0`，`minSdk=26`，`targetSdk=34`
- [PASS] `apksigner verify --verbose --print-certs`：Android Debug 证书，APK Signature Scheme v2
- [PASS] `zipalign -c -P 16 -v 4`：ZIP 对齐通过
- [CORRECTED] 首次定向测试在 Kotlin 编译阶段因 `Modifier.offset` 导入路径错误而失败；改为 `androidx.compose.foundation.layout.offset` 后，同一组定向测试、显式 Kotlin 编译和 APK 构建全部通过。
- [NOT COUNTED] `check_fresh_clone.py` 的子模块初始化进程自然结束，但外层工具超时后未保留最终输出，因此不作为本轮通过证据。

## 2026-07-25 首页细化构建结果

- [PASS] `git diff --check`
- [UPDATED] `KiyoriSoftwareHomeSearchTest` 将旧纵向偏置断言替换为搜索框中线 `38.2%` 精确值和短高度标题可见约束。
- [PASS] `.venv\\Scripts\\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
- [PASS] `:app:testDebugUnitTest` 定向执行 `KiyoriWeatherModelsTest`、`PendingAiHomeActionHandlerTest` 和 `KiyoriSoftwareHomeSearchTest`：`BUILD SUCCESSFUL in 1m 12s`，`155 actionable tasks`。
- [PASS] `:app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL in 15s`，`79 actionable tasks`。
- [PASS] `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 35s`，`230 actionable tasks`，其中 `25 executed`。
- [PASS] `app/build/outputs/apk/debug/app-debug.apk`：`2026-07-25 16:39:18 +08:00`，`449495558` bytes，SHA-256 `A5005FF1097DE7685D7E9AF79E4EE2B251BD33DA3C65424FB5B17F5F7377FA54`。
- [PASS] APK 元数据：`com.kiyori`，`versionCode=45`，`versionName=0.1.0`，`minSdk=26`，`targetSdk=34`。
- [PASS] `apksigner verify --verbose --print-certs`：Android Debug 证书，APK Signature Scheme v2。
- [PASS] `zipalign -c -P 16 -v 4`：ZIP 对齐通过。
- [PENDING] 手机和平板上的视觉、定位权限与端到端交互验收。

## 2026-07-25 首页与搜索交互细化构建结果

- [PASS] `git diff --check`。
- [PASS] `.venv\\Scripts\\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`。
- [PASS] `:app:testDebugUnitTest` 定向执行 `KiyoriSoftwareHomeSearchTest`、`PendingAiHomeActionHandlerTest`、`WebSessionProfilePolicyTest` 和 `KiyoriWeatherModelsTest`：`BUILD SUCCESSFUL in 3m 25s`，`155 actionable tasks`，其中 `16 executed`。
- [PASS] `:app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL in 38s`，`79 actionable tasks` 均为最新状态。
- [PASS] `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 3m 48s`，`230 actionable tasks`，其中 `25 executed`。
- [PASS] `app/build/outputs/apk/debug/app-debug.apk`：`2026-07-25 17:32:23 +08:00`，`449497606` bytes，SHA-256 `EDBC56792B55057613275AE43A297542C9322134B15CB6E3D346A41157509E26`。
- [PASS] APK 元数据：`com.kiyori`，`versionCode=45`，`versionName=0.1.0`，`minSdk=26`，`targetSdk=34`。
- [PASS] `apksigner verify --verbose --print-certs`：Android Debug 证书，APK Signature Scheme v2。
- [PASS] `zipalign -c -P 16 -v 4`：16 KB ZIP 对齐通过。
- [PENDING] 真机确认 Classic 与 Agent 两种输入样式均弹出 IME，软件首页和浏览器顶部两个搜索入口均显示无痕控件，并完成手机和平板视觉验收。

## 2026-07-25 无痕生命周期与窗口卡片构建结果

- [PASS] `git diff --check`。
- [PASS] `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`。
- [PASS] 定向 `:app:testDebugUnitTest`：`WebSessionProfilePolicyTest` 13 个、`BrowserThumbnailLayoutTest` 3 个、`WebSessionBrowserChromeLayoutTest` 3 个、`KiyoriSoftwareHomeSearchTest` 8 个、`PendingAiHomeActionHandlerTest` 2 个、`KiyoriWeatherModelsTest` 4 个，共 33 个测试，零失败、零错误、零跳过；`BUILD SUCCESSFUL in 4m 58s`。
- [PASS] `:app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL in 39s`，`79 actionable tasks`。
- [PASS] `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 3m 39s`，`230 actionable tasks`，其中 `26 executed`。
- [PASS] `app/build/outputs/apk/debug/app-debug.apk`：`2026-07-25 19:31:48 +08:00`，`449498358` bytes，SHA-256 `A424411508DD06B99A4593A8C323A30EBAF5E8E0C3F722A8B86D46F7A168F44F`。
- [PASS] APK 元数据：`com.kiyori`，`versionCode=45`，`versionName=0.1.0`，`minSdk=26`，`targetSdk=34`，应用标签 `Kiyori`。
- [PASS] `apksigner verify --verbose --print-certs`：Android Debug 证书，APK Signature Scheme v2。
- [PASS] `zipalign -c -P 16 -v 4`：16 KB ZIP 对齐通过。
- [PENDING] 真机验证无痕 Cookie、缓存和 WebStorage 隔离，进程内代际换新、冷启动旧代际删除、普通历史不受无痕访问影响，以及手机和平板纵向窗口卡片视觉。
