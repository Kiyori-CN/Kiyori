---
status: completed
plan_version: 3
milestone: M-02
baseline: 176f803e683307aa8e182fb357f35f84755c5f8b
origin_main: 62464b054f6de00b70c5596295bc216eb8edf63d
upstream_baseline: 0921f749a087c4a52a2202abdf32491ae335dd41
device_scope: excluded
last_reviewed: 2026-08-01
---

# M-02 Application 全局访问平台化精确清单

## 当前结论

M-02 只收口以下四类全局访问，不移动 `KiyoriApplication`：

1. 进程内 application context
2. 进程内全局 `Json`
3. 进程启动时间
4. 主进程初始化请求

当前 `KiyoriApplication` 在 `app/src/main/java` 中共有 42 次符号出现，分布在
Application 自身和 13 个直接消费者。外部消费者中：

- 10 个文件读取 Application context 或字符串资源
- `AppLogger.kt` 同时读取启动时间
- 3 个 Android 主进程入口请求首屏或完整初始化
- 全局 `Json` 没有外部直接消费者
- `globalImageLoader` 没有外部直接消费者

M-02 完成后，`com.ai.assistance.operit` 代码不再导入、强转或完全限定引用
`KiyoriApplication`。Application 仍位于
`com.ai.assistance.operit.core.application.KiyoriApplication`，Manifest、namespace、
application ID、多进程、初始化顺序、锁、线程、异常处理、数据、协议和 native 合同均不变。
Application 包迁移属于后续独立 M-03。

## 里程碑合同

```text
Milestone ID: M-02
Title: Application 全局访问平台化
Owner: Kiyori platform
Plan version: 3
Baseline commit: 176f803e683307aa8e182fb357f35f84755c5f8b
Upstream baseline: 0921f749a087c4a52a2202abdf32491ae335dd41
Scope: application context、全局 Json、app startup time、主进程初始化请求
Non-goals: Application 包迁移、MainActivity 迁移、OperitApp 改名、UI/数据/协议/native/terminal 变化
Source paths: 本文“精确源码影响清单”
Target paths: com.kiyori.platform.android / lifecycle / serialization
Rename map: 无
Stable contracts touched: 无；所有稳定 snapshot 必须零漂移
State owners: 每进程一个 Context、一个 Json、一个 startup time；唯一 KiyoriApplication 负责真实启动编排
Upstream sync class: Application 为 B；13 个消费者按现有 A/B owner 保持原路径
Risk IDs: R-01、R-04、R-05、R-10、R-11、R-15、R-17
Rollback point: main@176f803e + Kiyori-final-176f803e.bundle
Required commands: architecture、formal、Python、JVM、assembleDebug、APK audit
Required tests: Application platform contract、ARCH017、现有启动/App Shell 测试、full JVM
Required APK audit: package/version/label/Application/signature/ABI/16 KB/native packaging
Device acceptance: excluded；不得把本地证据表述为设备证据
Commit boundary: 当前未获提交授权；不得提交或推送
Open questions: 无实质歧义
```

## 基线与恢复证据

首次项目写入前已重新核验：

- 当前分支：`main`
- HEAD：`176f803e683307aa8e182fb357f35f84755c5f8b`
- origin/main 本地与远端：`62464b054f6de00b70c5596295bc216eb8edf63d`
- ahead/behind：`14/0`
- 工作树与索引：clean
- terminal gitlink：`8d5c2c224317c0176c14520facbb202a8be5993f`
- `tools/hotbuild/OperitNightlyRelease`：仍未初始化
- bundle：
  `D:\10_Project\Kiyori-backups\2026-07-31-pre-refactor-62464b05\Kiyori-final-176f803e.bundle`
- bundle：58 refs、complete history、`git bundle verify` 通过
- 基线 APK：
  `app/build/outputs/apk/debug/app-debug.apk`
- 基线 APK：467750448 bytes，SHA-256
  `B90852255517C003CAFBDC32E28E624D379D88397CBE6D2B7DAAB8BD2BB51D4A`

不得覆盖上述 bundle、恢复目录或基线 APK。

## 当前初始化与多进程语义

Android 为每个进程创建独立 Application 实例和独立静态内存。当前 Manifest 的独立进程为：

- `:crash`
- `:repair`
- `:player`

`MainActivity`、`AIForegroundService` 和 `FloatingChatService` 没有独立
`android:process`，因此三类主初始化请求都发生在默认主进程。

每个进程的 `KiyoriApplication.onCreate()` 当前按以下顺序执行：

1. 调用 `super.onCreate()`
2. 记录 `System.currentTimeMillis()`
3. 写入 `appStartupTimeMs`
4. 写入 `instance`
5. 配置 OpenMP 环境
6. 非 crash 进程安装全局异常处理器
7. 创建带 `SerializationSetup.module` 的全局 `Json`

M-02 必须保持这一相对顺序。新的 context、Json 和启动时间合同仍在每个进程
`onCreate()` 中建立；主初始化接口只由默认主进程中的 Activity/Service 请求，不在
`:crash`、`:repair` 或 `:player` 主动执行。

## 精确源码影响清单

### Application 与四类合同

| 当前路径 | 当前职责 | M-02 处理 |
| --- | --- | --- |
| `app/src/main/java/com/ai/assistance/operit/core/application/KiyoriApplication.kt` | 唯一 Android Application；持有 `instance`、`json`、`appStartupTimeMs`、初始化锁与实现 | 保留路径；移除三个 companion 全局值与无消费者的 `globalImageLoader`；在原时序安装三个窄平台值；实现主初始化接口；初始化方法体、锁、flags、线程和异常路径不改 |

新增四个窄职责文件：

| 目标路径 | 唯一职责 | 生命周期与可见性 |
| --- | --- | --- |
| `app/src/main/java/com/kiyori/platform/android/ApplicationContextAccess.kt` | 暴露当前进程的 `applicationContext` | `Application.onCreate()` 早期安装；每个进程独立；安装前访问继续以未初始化错误失败 |
| `app/src/main/java/com/kiyori/platform/serialization/ApplicationJson.kt` | 暴露当前进程唯一全局 `Json` | 仍由 Application 使用相同配置创建并安装；不创建第二 Json owner |
| `app/src/main/java/com/kiyori/platform/lifecycle/ApplicationStartupTime.kt` | 暴露当前进程启动 epoch milliseconds | 初始值仍为 `0L`；`onCreate()` 使用当前同一时间点记录 |
| `app/src/main/java/com/kiyori/platform/lifecycle/MainApplicationInitialization.kt` | 定义首屏必需初始化与完整主进程初始化请求 | `KiyoriApplication` 是真实实现；Activity/Service 只依赖接口；不持有第二 Application 引用 |

禁止创建统一的 `KiyoriPlatform`、`ApplicationServices`、类型到实例的 Map、按 key/class
解析的注册表或其他 Service Locator。

### application context 消费者

| 路径 | 当前访问 | 新访问 | 行为保持 |
| --- | --- | --- | --- |
| `data/api/MarketStatsApiService.kt` | `GitHubAuthPreferences.getInstance(KiyoriApplication.instance)` | `ApplicationContextAccess.current` | 构造时机与 preferences owner 不变 |
| `data/converter/GenericJsonConverter.kt` | 4 次 `instance.getString(...)` | `ApplicationContextAccess.current.getString(...)` | 文案、异常封装与解析行为不变 |
| `data/preferences/WakeWordPreferences.kt` | 3 个 lazy 默认字符串 | `ApplicationContextAccess.current.getString(...)` | lazy 时机和既有 `runCatching` 行为不变 |
| `core/config/SystemPromptConfig.kt` | `SkillRepository.getInstance(instance.applicationContext)` | 使用函数已有 `context.applicationContext` | 不新增全局访问；现有异常处理不变 |
| `util/AppLogger.kt` | 3 处 `instance.applicationContext` | `ApplicationContextAccess.current` | M-02 保持当时的文件位置、同步、异常吞吐和早期绑定路径；M-05B 后续把静态 `boundContext` 收敛为提前解析的 `filesDir: File`，不改变日志目录 |
| `ui/common/markdown/MarkdownCodeTypeface.kt` | 无参重载读取全局 context | `ApplicationContextAccess.current` | 字体缓存、资源与 monospace 既有异常路径不变 |
| `plugins/toolbox/ToolboxPlugin.kt` | 注册时读取全局 context | `ApplicationContextAccess.current` | PluginRegistry 顺序和单例 manager 不变 |
| `plugins/toolpkg/ToolPkgHookBridgeSupport.kt` | package manager helper 读取 context | `ApplicationContextAccess.current` | 同一 PackageManager/AIToolHandler owner |
| `plugins/toolpkg/ToolPkgToolLifecycleBridge.kt` | 注册时读取 context | `ApplicationContextAccess.current` | channel、listener、hook 注册顺序不变 |
| `ui/features/token/model/UrlConfig.kt` | 默认 tabs 的 4 个资源字符串 | `ApplicationContextAccess.current.getString(...)` | 默认对象、序列化默认值与本地化流程不变 |

`SystemPromptConfig.getSystemPrompt()` 已有 `Context` 参数，因此该文件直接使用既有参数，
不经过全局 platform context。这是依赖显式化，不扩大影响到调用者。

### app startup time 消费者

| 路径 | 当前访问 | 新访问 | 行为保持 |
| --- | --- | --- | --- |
| `util/AppLogger.kt` | `KiyoriApplication.appStartupTimeMs` | `ApplicationStartupTime.epochMillis` | `takeIf { > 0L }` 与当前文件命名时机不变 |

### 主进程初始化请求消费者

| 路径 | 当前访问 | 新访问 | 行为保持 |
| --- | --- | --- | --- |
| `ui/main/MainActivity.kt` | 强转 `KiyoriApplication`，同步请求首屏初始化，首帧后请求完整初始化 | 强转稳定 `MainApplicationInitialization` 接口 | `onCreate` 调用位置、Choreographer 一帧边界、Dispatcher、Activity 状态检查不变 |
| `api/chat/AIForegroundService.kt` | `onCreate()` 强转 Application 并请求完整初始化 | 强转稳定接口 | `isRunning` 写入与初始化的相对顺序不变 |
| `services/FloatingChatService.kt` | `onCreate()` 强转 Application 并请求完整初始化 | 强转稳定接口 | `super.onCreate()` 后立即请求的顺序不变 |

接口只有：

```kotlin
fun initializeMainUiPrerequisites()
fun initializeMainApplication()
```

不增加异步包装、回调、Result、nullable delegate 或重试路径。

### 全局 Json

当前外部直接消费者为 0。M-02 仍迁移该 owner，是为了在 M-03 前移除
Application companion 全局状态，并保证未来 Operit AI 只能依赖稳定 platform 合同。

`ApplicationJson.current` 必须使用当前完全相同的配置：

- `serializersModule = SerializationSetup.module`
- `ignoreUnknownKeys = true`
- `isLenient = true`
- `prettyPrint = false`
- `encodeDefaults = true`

禁止因为当前无消费者而删除 `SerializationSetup.module`、改变创建时机或换成新的默认
`Json`。

## 不允许触碰的文件与合同

M-02 不修改：

- `app/src/main/AndroidManifest.xml`
- `app/lint-baseline.xml`
- Gradle namespace、application ID、version、依赖与模块
- AIDL、JNI、native、ProGuard/R8、Room、ObjectBox、WorkManager worker/scheduler
- DataStore、SharedPreferences、数据库、备份格式与公共目录
- `OperitApp`、主题、Pager、Back、抽屉、Browser、Player、设置 UI
- `terminal` 内容与 gitlink
- `tools/hotbuild/OperitNightlyRelease`

现有 snapshot 必须保持逐项不变：

- Manifest component 与 semantic structure
- stable identifiers
- persistence names 与 API calls
- native / IPC identifiers
- critical file hashes

## 测试替身与特征测试设计

### 平台合同测试

新增 `ApplicationPlatformContractTest`：

- 使用 Mockito Context 替身验证 context 合同只保留
  `applicationContext`
- 使用测试 `Json` 验证安装后返回同一实例
- 验证 startup time 初始为 `0L`，记录后逐字返回
- 使用 fake `MainApplicationInitialization` 验证两个请求接口互不替代

测试不得创建或模拟第二 `KiyoriApplication` singleton。

### 架构门禁

新增 `ARCH017`：

1. 四个 contract 文件出现任意一个后，其余三个必须同时存在。
2. 除 `KiyoriApplication.kt` 自身外，`com.ai.assistance.operit` 源码中
   `KiyoriApplication` 代码引用必须为 0；注释和字符串不计依赖。
3. `KiyoriApplication` 不得继续声明 `instance`、`json`、
   `appStartupTimeMs` 或 `globalImageLoader`。
4. 三个 process 安装方法只能在 contract 定义和唯一 Application 中调用。
5. platform contract 不得出现统一 Service Locator/registry 形态。
6. `KiyoriApplication` 必须实现 `MainApplicationInitialization`。

门禁在四个 M-02 contract 尚未出现时保持 M-01 基线通过；第一个 contract 文件出现后，
M-02 进入 active 状态，直到四个文件与全部消费者闭合前明确失败。

### 现有行为回归

至少执行：

- `ci.test.test_architecture_boundaries`
- full `ci/test`
- `:app:compileDebugKotlin`
- Application/App Shell 启动相关定向 JVM tests
- full `:app:testDebugUnitTest`
- `:app:assembleDebug`

## 实施顺序

1. [DONE] M-02A 只读影响分析、进程分析、精确文件与旧访问映射
2. [DONE] M-02B 合同、测试替身、门禁、回滚和停止条件设计
3. [DONE] 先落地 `ARCH017` 与负向测试，确认 M-01 基线仍通过
4. [DONE] 新增四个窄 platform contract 与合同测试
5. [DONE] 在 Application 原时序安装三个 process 值，并实现主初始化接口
6. [DONE] 按本文逐文件替换 13 个直接消费者
7. [DONE] 反向搜索旧 concrete dependency、第二 owner 与 locator
8. [DONE] architecture、formal、Python、JVM、Debug APK 和产物审计
9. [DONE] 更新 `CONTEXT.md`、本文状态、任务日记与最终恢复证据

## 实施与验证结果

- 新增：
  - `com.kiyori.platform.android.ApplicationContextAccess`
  - `com.kiyori.platform.serialization.ApplicationJson`
  - `com.kiyori.platform.lifecycle.ApplicationStartupTime`
  - `com.kiyori.platform.lifecycle.MainApplicationInitialization`
- `KiyoriApplication` 仍在原包，仍是唯一 Application、初始化锁和 initialized flags owner。
- Application 在原 `onCreate()` 相对顺序记录 startup time、安装 application context 和
  完全相同配置的 Json；初始化方法体、Choreographer 边界、Dispatcher、线程和异常路径未改。
- 13 个外部消费者不再引用 `KiyoriApplication`；Operit 源码中的符号只剩 Application 自身。
- 删除无外部消费者的 `globalImageLoader`，并移除 companion 的 `instance`、`json` 和
  `appStartupTimeMs`。
- `ARCH017` 还会拒绝 installer 的直接导入、别名绕过、第二 process owner 和统一
  Service Locator。
- 架构专测：`41/41` 通过。
- 完整 Python：`107/107` 通过。
- platform contract：`2/2` 通过。
- 启动/App Shell 定向 JVM：通过。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- architecture gate：PASS，`phase=post-m01`。
- formal readiness：PASS。
- Markdown links：0 errors、0 warnings。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 大小：469854720 bytes
  - SHA-256：`F63AEFAADA4C712AFBD3FDE4D851490058D0A8E0E799C8104ACD13516F209DC9`
  - package：`com.kiyori`
  - versionCode：45
  - versionName：0.1.0
  - label：Kiyori
  - Manifest Application：
    `com.ai.assistance.operit.core.application.KiyoriApplication`
  - ABI：`arm64-v8a`
  - arm64 native entries：53
  - Android Debug V2 签名：通过
  - 16 KB zipalign：`Verification successful`
- Manifest、Gradle、stable/persistence/native/IPC snapshot、terminal 和远端均未修改。
- 未提交、未推送、未操作手机、模拟器或 ADB。

## 停止条件

出现以下任一情况立即停止后续源码写入：

- 需要移动 `KiyoriApplication` 包路径
- 需要改变 Manifest、namespace、Application ID 或 Android 组件名
- context、Json 或 startup time 出现第二 owner
- 需要统一 registry、Service Locator、nullable delegate、重试或 fallback
- 初始化锁、flags、调用顺序、Choreographer 边界、Dispatcher 或异常路径发生变化
- 独立进程开始执行主进程初始化
- 13 个消费者之外出现无法解释的 concrete Application 依赖
- stable/persistence/native/IPC/critical snapshot 漂移
- terminal gitlink 或内容变化
- architecture、formal、测试、构建或 APK 审计失败且不能在 M-02 根因范围内修复

## 反向审查

M-02 封板前逐项回答：

1. 每个进程何时安装 context、Json 和 startup time？
2. 安装前访问是否仍明确失败，而不是返回 null、默认值或替代实例？
3. `KiyoriApplication` 是否仍是唯一 Android Application 和真实启动编排实现？
4. MainActivity 与两个 Service 的调用顺序是否逐行保持？
5. `mainInitializationLock` 和两个 initialized flags 是否仍只有一个 owner？
6. `com.ai.assistance.operit` 是否只导入 `com.kiyori.platform` 合同，不导入 app 实现？
7. 当前没有消费者的全局 Json 是否保持相同配置和进程初始化时机？
8. 是否出现第二 Context、第二 Json、第二初始化 coordinator 或通用 locator？
9. Manifest、数据、协议、WorkManager、AIDL、JNI、native 和 terminal 是否零变化？
10. 最近恢复点是否仍能还原 `main@176f803e`？
