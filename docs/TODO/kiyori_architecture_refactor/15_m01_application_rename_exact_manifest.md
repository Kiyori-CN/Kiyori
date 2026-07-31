---
status: implemented
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-07-31
---

# M-01 Application 原包改名精确影响清单

## 结论

M-01 继续作为第一个应用源码里程碑，但不能在没有机器可读稳定合同基线时直接执行。

推荐顺序：

```text
M-00：方案 v3 与正式文档封版
    -> G-00：架构所有权与稳定合同门禁基线
    -> M-01：原包内 OperitApplication -> KiyoriApplication
```

G-00 不修改 Android 运行时代码。它先固定允许文件、Manifest 入口、数据/协议字符串和
纯改名比较规则，使 M-01 的实际差异可以被机器检查，而不是仅依赖人工判断。

## 当前精确影响面

基于 `main@62464b054f6de00b70c5596295bc216eb8edf63d`：

- Kotlin 文件：14 个
- Kotlin 中 `OperitApplication` 出现：42 次
- Manifest 出现：1 次
- Lint baseline 路径出现：6 次
- 总受影响文件：16 个
- 总出现次数：49 次
- 直接引用该类名的 JVM/Android 测试：0 个
- `OperitApplication.kt` 当前长度：710 行

“42”表示 Kotlin 源码中的出现次数，不是 42 个文件。此前方案中的模糊表述以本清单为准。

## 允许文件清单

### 类定义

```text
app/src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt
```

目标文件：

```text
app/src/main/java/com/ai/assistance/operit/core/application/KiyoriApplication.kt
```

package 必须继续为：

```text
com.ai.assistance.operit.core.application
```

### Android 入口

```text
app/src/main/AndroidManifest.xml
```

只允许：

```text
.core.application.OperitApplication
-> .core.application.KiyoriApplication
```

### 13 个 Kotlin 消费者

```text
app/src/main/java/com/ai/assistance/operit/api/chat/AIForegroundService.kt
app/src/main/java/com/ai/assistance/operit/core/config/SystemPromptConfig.kt
app/src/main/java/com/ai/assistance/operit/data/api/MarketStatsApiService.kt
app/src/main/java/com/ai/assistance/operit/data/converter/GenericJsonConverter.kt
app/src/main/java/com/ai/assistance/operit/data/preferences/WakeWordPreferences.kt
app/src/main/java/com/ai/assistance/operit/plugins/toolbox/ToolboxPlugin.kt
app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgHookBridgeSupport.kt
app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgToolLifecycleBridge.kt
app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt
app/src/main/java/com/ai/assistance/operit/ui/common/markdown/MarkdownCodeTypeface.kt
app/src/main/java/com/ai/assistance/operit/ui/features/token/model/UrlConfig.kt
app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt
app/src/main/java/com/ai/assistance/operit/util/AppLogger.kt
```

### Lint baseline

```text
app/lint-baseline.xml
```

只允许把 6 个旧文件路径改为新文件路径。问题 ID、message、line、column 和 baseline 条目数量
均不得变化。

## 允许的符号变化

```text
OperitApplication -> KiyoriApplication
operitApplication -> kiyoriApplication
this@OperitApplication -> this@KiyoriApplication
"OperitApplication" -> "KiyoriApplication"
Application class for Operit -> Application class for Kiyori
```

除上述替换、文件名变化、直接 import 和 Manifest 精确引用外，不允许函数体、初始化顺序、
异常处理、锁、线程、协程、默认值、日志正文、资源、数据库、WorkManager 或服务逻辑发生变化。

## 必须冻结的 Application 合同

M-01 前后必须保持：

- `Application`、`ImageLoaderFactory`、`WorkConfiguration.Provider` 三个基类/接口不变
- `json`、`appStartupTimeMs`、`instance` 和 `globalImageLoader` 的可见性与语义不变
- `onCreate`、`attachBaseContext`、`onTerminate`、`onLowMemory`、`onTrimMemory` 行为不变
- `initializeMainUiPrerequisites` 与 `initializeMainApplication` 的锁、幂等和调用顺序不变
- main、crash、repair 和 `:player` 进程分支不变
- WorkManager 配置、ImageLoader、数据库预热、插件生命周期和后台初始化不变
- `package com.ai.assistance.operit.core.application` 不变
- application ID、Gradle namespace、版本、Manifest 其他组件和所有稳定合同不变

## G-00 门禁要求

正式执行 M-01 前，通用架构门禁至少要能检查：

1. 当前候选差异只触及本清单允许的实现文件和相称文档。
2. 旧文件与新文件在规范化替换名称后内容一致。
3. Manifest 只改变 Application 类名。
4. Lint baseline 只改变 6 个文件路径。
5. Kotlin 中旧符号归零，新符号出现次数与基线一致。
6. package declaration 未变。
7. 数据、协议、Intent、WorkManager、AIDL、JNI 和 native snapshot 零变化。
8. terminal gitlink、未初始化 hotbuild gitlink 和私密配置零变化。

## 派生控制面元数据

16 个实现文件仍是完整且不可扩大的 Android 差异范围。由于
`app/lint-baseline.xml` 的 6 个受审计 location 路径发生纯文件名变化，
`ci/script/normalize_lint_baseline.py` 中的 `EXPECTED_SHA256` 和
`ci/README.md` 中记录的当前归一化 SHA-256 必须同步更新。它们是由已批准
lint baseline 路径改名机械派生的 CI 元数据，不属于 Android 实现文件，不改变 lint issue
集合、运行行为、数据或协议。

若新校验和对应的 diff 除 6 个 Application 文件路径外还有任何 lint issue、message、line
或 column 变化，立即停止 M-01。

G-00 应复用
[架构门禁与机器可读所有权规范](13_architecture_guard_specification.md)，不能为 M-01 建立
一次性脚本后遗留在仓库。

## 验证顺序

1. 上游基线、HEAD、工作树和备份状态通过。
2. G-00 架构门禁通过。
3. `git diff --summary --find-renames=90%` 识别文件重命名。
4. 旧符号反向搜索仅命中文档历史。
5. Kotlin 编译通过。
6. Application 启动合同静态测试通过。
7. formal readiness 通过。
8. Debug APK 构建通过。
9. APK Manifest 能解析 `com.ai.assistance.operit.core.application.KiyoriApplication`。
10. package、version、签名、ABI、native packaging 与基线一致。
11. 差异反向审查通过后才创建本地 M-01 提交。

## 停止条件

出现以下任一情况停止 M-01：

- 影响文件超出允许清单且不能由相称文档解释
- 规范化后 Application 文件存在非命名差异
- Lint baseline 条目数量或问题内容变化
- Manifest 除 Application 类名外出现变化
- 编译、Application 合同检查、formal readiness 或 Debug 构建失败
- 上游在冻结基线后修改了 Application 或任一直接消费者
- 需要同时改变 package、namespace、初始化职责或状态 owner

最后一项表示当前工作已超出纯改名，应重新规划 M-02，不能扩大 M-01。

## 实施与验证结果

- 16 个 Android 实现文件精确命中，规范化后逐文件等价
- Kotlin 旧符号 `0`，新符号 `42`；Manifest `1`；lint baseline location `6`，总计 `49`
- package 仍为 `com.ai.assistance.operit.core.application`
- `:app:compileDebugKotlin` 通过
- 3 组启动/App Shell 定向测试通过
- 完整 `:app:testDebugUnitTest`：`771` 项，`0` failure、`0` error、`0` skipped
- formal readiness、88 项 CI 门禁测试、lint baseline checksum 和 M-01 architecture gate 通过
- `:app:assembleDebug` 通过，player native packaging gate 通过
- APK 为 `com.kiyori`、versionCode `45`、versionName `0.1.0`、`arm64-v8a`，
  v2 签名与 16 KB zipalign 通过
- APK Manifest 入口为
  `com.ai.assistance.operit.core.application.KiyoriApplication`
- 新 APK SHA-256 为
  `B90852255517C003CAFBDC32E28E624D379D88397CBE6D2B7DAAB8BD2BB51D4A`
- 与基线 APK 的 5,799 个 ZIP 条目集合完全一致；内容变化仅 Manifest 和 11 个 dex，
  压缩内容总量只差 180 字节。基线 APK 的额外体积来自约 26.4 MB 历史条目间空洞，
  干净构建后不再保留，不是资产或 native 缺失

额外执行的 `:app:lintDebug` 报告 27 个 error，全部位于 3 个未修改文件，
与 M-01 允许文件交集为 0，且没有 Application 类名相关诊断。该既有 lint 债务不在本里程碑
范围内，未通过更新 baseline 或修改无关源码掩盖。
