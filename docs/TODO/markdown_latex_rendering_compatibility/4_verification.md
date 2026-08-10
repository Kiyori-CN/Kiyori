# 4. 验证记录

当前状态：源码、测试源码、文档、本地自动化验证和 Debug APK 构建均已完成。目标设备上的
公式视觉与交互验收尚未执行，交付状态保持 `verification_pending`。

## 已执行检查

### 1. 定向 JVM 回归

串行执行 Browser 与 Markdown/LaTeX 联合定向测试，覆盖：

- `LatexFormulaSupportTest`
- `DisplayMathExpressionTest`
- `NativeMarkdownSplitterTest`
- `BrowserViewportPolicyTest`
- `BrowserClickNavigationPolicyTest`
- `BrowserRunCodeContractTest`
- `UserscriptBridgeAuthorizationPolicyTest`
- `UserscriptBootstrapScriptSecurityTest`

结果：`8` 个测试类、`63/63` 通过，失败、错误和跳过均为 `0`；主源码与单元测试源码编译通过。

### 2. AndroidTest 编译

执行：

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL`，`146` 个任务，其中 `7` executed、`3` from cache、
`136` up-to-date。唯一警告来自既有 `EnhancedTableBlockAndroidTest` 使用弃用的 Compose
测试 API，与本轮无关。

### 3. 项目门禁与文档检查

- 项目 `.venv` 的 formal readiness 通过
- `git diff --check` 通过
- 本轮修改/新增的 `9` 份 Markdown 工作树级本地链接检查通过，破损链接为 `0`

### 4. Debug 构建与 APK

执行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

最终收尾构建结果：`BUILD SUCCESSFUL`，`238` 个 actionable tasks；单 Launcher 校验与
`verifyDebugPlayerRuntimePackaging` 通过。

产物：

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：`475435609` bytes
- 修改时间：`2026-08-10 21:45:34 +08:00`
- SHA-256：`FA53014E1D66469723AD99F72CC85DC39B22A9C967983B35612A4C725849ED04`
- application ID：`com.kiyori`
- versionCode：`45`
- versionName：`0.1.0`
- min SDK：`26`
- target SDK：`34`
- compile SDK：`37`
- ABI：仅 `arm64-v8a`
- 原生库：`51`，basename 重复为 `0`
- `assets/accessibility.apk`：恰好 `1`
- `assets/packages/browser.js`：恰好 `1`
- Android Debug v2 签名：通过，signer 为 `1`
- `zipalign -c -P 16 -v 4`：`Verification successful`

## 设备边界

本轮没有安装 APK，也没有执行 ADB/MuMu/真机操作。真实设备上的公式视觉、横向拖动与
纵向滚动协同、流式输入时序和不同屏宽仍需后续用户/设备验收。Browser Android JS suite
已写入并通过编译/打包，但也尚未在目标设备执行。
