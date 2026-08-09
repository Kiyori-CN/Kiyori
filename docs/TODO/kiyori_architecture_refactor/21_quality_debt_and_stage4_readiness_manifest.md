---
status: local_validation_complete
baseline_commit: 6b6493a0bfd12072116e45fb733d551fad13e32b
current_phase: delivery-audit
device_scope: excluded
release_scope: excluded
---

# Stage 4 前质量债务与开发就绪精确清单

## 目标

本清单在 M-05 design/theme/platform 完成后、阶段 4 Browser 产品域开始前执行。目标是把
当前未基线化 Lint、可验证工程警报和高风险历史债务收口到明确 owner，避免在 Browser
这个高风险领域上继续叠加已知正确性、资源、平台和依赖问题。

本轮允许修改源码、资源、构建配置、测试和权威文档，也允许在完整审计后提交并推送
`main`。本轮不部署、不安装 APK、不操作设备或模拟器、不执行 Release 构建，也不改变
稳定协议、持久化格式、AIDL/JNI 名称、Android 外部组件名或未批准的用户行为。

任何修复都必须解决真实根因。禁止通过新增 suppress、扩大 Lint baseline、关闭检查、
降低断言、复制 owner、引入 fallback 或批量升级无关依赖来制造表面全绿。

## 可恢复基线

- branch：`main`
- checkpoint：`6b6493a0bfd12072116e45fb733d551fad13e32b`
- checkpoint message：`refactor(architecture): seal Kiyori app and platform ownership`
- local tracking baseline：`origin/main@62464b054f6de00b70c5596295bc216eb8edf63d`
- checkpoint fresh clone：PASS
- formal readiness：PASS
- M-05 final architecture：`phase=m03`、`errors=[]`
- Python：`166/166`
- JVM：`137 suites / 822 tests / 0 failures / 0 errors / 0 skipped`
- Debug APK：`477957302` bytes
- Debug APK SHA-256：
  `E6A5E78CFB4441399DC36D83583BF6F441441E3736A85A17759695D73ACF790C`

## 当前 Lint 债务

质量清理初始报告包含 `317` 条未基线化记录。QD-05 完成并剪枝失效 baseline 后，fresh
full lint 报告只包含 `1` 条 XML 记录：

| 严重级别 | 数量 | 说明 |
| --- | ---: | --- |
| Error | 0 | QD-01 已清除 22 条缺失翻译和 5 条 Compose 资源读取错误 |
| Warning | 0 | QD-01 至 QD-05 已清除全部 current-only warning |
| Hint | 1 | 现有 baseline 状态提示 |

Gradle 控制台不把 `LintBaseline` 状态提示计入 actionable hint，因此同一次执行摘要为
`Lint found no new issues`。本清单的结构化数量以
`app/build/reports/lint-results-debug.xml` 为准。

当前 `app/lint-baseline.xml` 另有 `5567` 条历史记录，完整 lint 汇总为
`1208 errors / 4210 warnings / 149 hints`。baseline SHA-256 为
`DDA9A10B3C899698271AECA2B3BE9812674D3041ADF20AF4EA6D15D80C552405`。
2026-08-09 的结构化交集只删除 `39` 条已失效记录，`current-only=0`，未把本轮问题
吸收到 baseline。

baseline 是历史债务清单，不是永久豁免。清理时只允许删除已经由当前源码证明失效或已经
修复的记录，禁止吸收任何 current-only 问题。

## 分批实施

### QD-01：当前正确性错误

状态：`completed`

范围：

- `MissingTranslation`：22
- `LocalContextGetResourceValueCall`：5

实施要求：

- 为 `ko`、`pt`、`ms`、`id`、`es` 补齐 Browser history 的 22 个字符串；占位符、
  格式化参数和 XML 转义必须与默认资源一致
- Compose 中需要响应 Configuration 变化的文本改用 `stringResource` 或
  `LocalResources`；不得用缓存字符串或 Context wrapper 绕开重组
- XML 解析、格式占位符检查和受影响 Compose 编译通过

完成信号：current-only `Error` 为 `0`。

验证证据：

- `ko`、`pt-rBR`、`ms`、`id`、`es` 均已补齐 22 个 Browser history 字符串
- 项目 `.venv` XML/键/占位符检查通过：`6 locales / 22 keys / placeholders consistent`
- `:app:compileDebugKotlin` 与
  `WebSessionHistoryPolicyTest`、`WebSessionUserscriptUiPolicyTest`、
  `WebSessionUserscriptManagementTest` 通过
- fresh `:app:lintDebug` 成功；`MissingTranslation=0`、
  `LocalContextGetResourceValueCall=0`、current-only `Error=0`
- 未修改 `app/lint-baseline.xml`，未新增 suppress 或 lint disable

### QD-02：行为保持型 Kotlin 与 Compose 现代化

状态：`completed`

范围：

- `UseKtx`：59
- `ObsoleteSdkInt`：3
- `ModifierParameter`：2
- `AutoboxingStateCreation`：2

实施要求：

- SharedPreferences 改用 KTX `edit`，保持 `apply`/`commit`、写入键和值完全一致
- URI、Bitmap、Canvas 和 Drawable 改用等价 KTX API，不改变异常和线程行为
- 删除 minSdk 26 下不可达的 SDK 分支，不增加兼容回退
- Modifier 调整只改变参数顺序，不改变调用值和 modifier 应用顺序
- Int Compose state 改为 primitive state

完成信号：上述四类 current-only 记录为 `0`，相关 JVM/编译检查通过。

验证证据：

- 52 处 SharedPreferences 写入改用 AndroidX KTX `edit`，仍使用默认异步
  `apply`，持久化键、值、条件删除和内存状态更新顺序不变
- 7 处 URI/Bitmap/Canvas/Drawable 调用改用等价 KTX API；缩略图仍按同一
  translate -> scale -> draw 顺序渲染
- 3 个 minSdk 26 下不可达的 SDK 分支已删除，未增加兼容回退
- 2 个 Composable 只调整 `Modifier` 参数位置，现有调用均使用命名参数；2 个整数状态改用
  `mutableIntStateOf`
- `:app:compileDebugKotlin` 和完整 `:app:testDebugUnitTest` 通过：
  `137 suites / 822 tests / 0 failures / 0 errors / 0 skipped`
- fresh `:app:lintDebug` 成功；`UseKtx=0`、`ObsoleteSdkInt=0`、
  `ModifierParameter=0`、`AutoboxingStateCreation=0`
- `:app:assembleDebug` 和 formal readiness 通过；未修改 baseline 或新增 suppress

### QD-03：资源、图片目录与复数

状态：`completed`

范围：

- `UnusedResources`：120
- `IconLocation`：28
- `PluralsCandidate`：17

实施要求：

- 每个 unused resource 先以 `R.*`、XML、Manifest、反射/动态名称和测试引用反向搜索；
  只有零引用且不属于外部资源合同的条目才能删除
- densityless bitmap 移入 `drawable-nodpi/`，保持资源名、原始字节和调用点不变
- 英文数量文案改为 `<plurals>`，同步所有语言的资源形状和调用方
- 不借资源清理改变图标、排版、功能入口或用户文案含义

完成信号：三类 current-only 记录为 `0`，资源合并、翻译和 Debug 构建通过。

验证证据：

- 逐名扫描 tracked 文本、`R.*`、XML/Manifest 引用和 `getIdentifier` 后，删除
  113 个无引用字符串键的 776 个多语言定义、3 个颜色和 4 个 drawable；动态资源访问仅
  指向 Android 系统 `status_bar_height`
- 26 个仍在使用的 PNG 从 `drawable/` 原字节移动到 `drawable-nodpi/`，移动前后
  SHA-256 全部一致，资源名和调用点不变
- 17 个数量文案改为 `plurals`；`values`/`en` 使用 `one/other`，
  `es`/`pt-rBR` 使用 `one/many/other`，`id`/`ms`/`ko` 使用 `other`
- 资源合并、Kotlin 编译和完整 JVM
  `137 suites / 822 tests / 0 failures / 0 errors / 0 skipped` 通过
- fresh `:app:lintDebug` 成功；`UnusedResources=0`、`IconLocation=0`、
  `PluralsCandidate=0`、`UnusedQuantity=0`、`MissingQuantity=0`
- 临时完整 baseline `5845` 条；结构化交集
  `retained=5787 / stale=4 / current-only=58`，只删除 3 条
  `MissingTranslation` 和 1 条 `PluralsCandidate` 失效记录

### QD-04：Android 与 Browser 平台合同

状态：`completed`

范围：

- `RequiresFeature`：19
- `StaticFieldLeak`：5
- `AppBundleLocaleChanges`：1
- `SelectedPhotoAccess`：1
- `SpecifyJobSchedulerIdRange`：1
- `ConfigurationScreenWidthHeight`：1
- `DiscouragedApi`：1
- `ViewConstructor`：1

实施要求：

- AndroidX WebKit API 必须在真实 feature contract 下调用；不增加第二 WebView/Profile
  owner，不以 catch 或 fallback 隐藏不支持状态
- 只允许持有 Application 生命周期对象；WebView/Activity/Service 引用必须有显式注册、
  释放和唯一 owner
- 动态语言切换、Android 14 selected media、JobScheduler ID、窗口宽度和播放器方向必须
  使用官方稳定合同，并保持现有产品行为或在未发布内部方案中完整替换旧实现
- 自定义 View 补齐工具构造合同，不复制渲染状态
- Kotlin 编译当前另报告同一 `WebSettings.databaseEnabled` 位置的 2 条弃用警报；该 API
  必须在本批按当前 WebView 合同移除或替换，禁止通过 suppress 保留

完成信号：上述八类 current-only 记录为 `0`，Browser/Player/启动定向测试通过。

验证证据：

- AndroidX WebKit multi-profile、isolated-world 和 reply-proxy API 均位于对应
  `WebViewFeature.isFeatureSupported(...)` 正向分支；不支持状态继续由既有 capability
  owner 显式拒绝，没有新增第二 Profile/WebView owner、catch 降级或 fallback
- `ApplicationContextAccess`、`BrowserDownloadSettingsStore` 与共享 Browser tools
  只持有 `Application` 生命周期对象；`WebSessionBrowserHost` 从 companion 静态字段移到
  唯一 `StandardBrowserSessionTools` 实例，Javascript bridge 显式持有该实例并沿现有
  WebView 销毁路径释放
- App Bundle 禁用 language split；Manifest 声明 Android 14 selected-media 权限；
  WorkManager 独占 `0x5000..0x53E8` 的 1,001 个含首尾 JobScheduler ID（满足
  WorkManager 的 `max - min >= 1,000` 前置条件），与 Browser runtime 的 `0x4B10`
  分离；宽屏判定改用 `LocalWindowInfo.containerSize`
- Player 默认横屏策略从 Manifest 重复声明收口到既有 Activity policy；自定义
  `PlayerSurfaceView` 改为框架 `SurfaceView` 加唯一 callback owner，fullscreen/floating
  的 surface role、overlay 和 session 注册顺序不变
- 6 处已经弃用的 `WebSettings.databaseEnabled` 写入全部删除，DOM storage 与其他
  WebView 配置保持
- `:app:compileDebugKotlin`、Player/平台定向测试、完整 JVM
  `137 suites / 822 tests / 0 failures / 0 errors / 0 skipped` 和 fresh
  `:app:lintDebug` 通过；
  `RequiresFeature`、`StaticFieldLeak`、`AppBundleLocaleChanges`、
  `SelectedPhotoAccess`、`SpecifyJobSchedulerIdRange`、
  `ConfigurationScreenWidthHeight`、`DiscouragedApi`、`ViewConstructor` 均为 `0`
- 临时完整 baseline `5814` 条；结构化交集
  `retained=5786 / stale=1 / current-only=28`，只删除已由 reply-proxy feature guard
  修复的 1 条 `RequiresFeature`
- 串行 `:app:assembleDebug` 成功并执行 `verifyDebugPlayerRuntimePackaging`；APK 为
  `467708509` bytes，SHA-256
  `40D3B61D111D5899E69BD0C51B059641565C4FC54A0435195A93A46FFD2BBC75`
- 完整 architecture `phase=m03`、36 项检查通过；门禁源码扫描排除 Debug native
  `.cxx` 生成目录并缓存同一源码快照的字符级掩码后，真实工作树从超过 15 分钟仍未完成
  收敛为可重复执行；本轮增加独立 Debug Manifest contract 后共 37 项检查，并重新锁定
  QD-02/QD-04 已审阅的 Manifest、Application、root、Software Home、navigation
  integration 与 PlayerActivity 当前保护值

### QD-07：Kotlin 编译器警报与弃用迁移

状态：`completed`

QD-03 的大范围资源变更触发完整 Kotlin 重编译，暴露出增量构建未重复显示的历史编译器
警报。它们必须在 Stage 4 前形成结构化清单，并按以下类别逐批处理：

- 非空类型上的安全调用、非空断言、恒真/恒假条件、无效 cast 与冗余转换
- Android、Compose、协程和第三方 API 弃用
- opt-in 与 delicate API 合同
- RTL AutoMirrored 图标和可访问性相关迁移

禁止以全局 compiler suppress、降低 warning 级别或关闭检查代替修复。确实属于稳定
override、第三方 ABI 或发布兼容合同的条目，必须记录精确 owner、保留理由和退出条件。

完成信号：强制完整 `:app:compileDebugKotlin` 日志中项目自有可修复警报为 `0`；保留项有
逐条审计记录，且编译、JVM、Lint 和 Debug APK 通过。

2026-08-03 使用 Kotlin `2.4.10`、compile SDK `37` 和 `--rerun-tasks` 建立了首份完整
机器清单：共 `552` 条，按模块为 `app=515 / terminal=27 / quickjs=6 /
dragonbones=2 / mnn=2`。按诊断族为：弃用 API `296`、非空值上的安全调用 `48`、
Java boxed 类型 `30`、恒定条件 `28`、冗余非空断言 `28`、冗余 Elvis `27`、
unchecked cast `25`、无效 cast `16`、其他精确诊断 `16`、when 完整性 `16`、
冗余转换 `10`、无效表达式 `7`、opt-in/delicate API `5`。日志位于
`app/build/reports/qd07-compile-debug.log`；它是工作区验证产物，不进入 Git。

实现批次已完成：

- 按模块清理 app、terminal、quickjs、dragonbones 与 mnn 的 552 条项目编译器警报，
  未启用全局 suppress、降低 warning 级别或关闭检查
- Android/Compose/Media3/WebKit 弃用 API 已迁到当前稳定合同；非空、cast、when、
  boxed type、opt-in、RTL 图标与无效表达式按实际类型和调用边界修复
- `app/libs/arsc.jar` 已从直接输入改为精确 JAR 依赖，旧 stack-map D8 警报不再出现
- terminal 的伪 `libsudo.so` 已删除；运行时由唯一 `TerminalManager` 生成
  `/system/bin/sh` 命令 shim，native strip 假警报不再出现
- 增量 `:app:compileDebugKotlin`、`:terminal:testDebugUnitTest` 与相关模块编译通过
- 最终 `:app:compileDebugKotlin --rerun-tasks` 在 4 分 41 秒内执行 `83/83` tasks；
  stdout 中项目 warning 为 0，stderr 为 0 bytes
- 完整 JVM 为 terminal `2 suites / 8 tests`、app `138 suites / 823 tests`，合计
  `140 suites / 831 tests / 0 failures / 0 errors / 0 skipped`
- 正式 `:app:lintDebug` 在 7 分 39 秒内通过，报告仅有 1 条 `LintBaseline` 状态 hint；
  current-only 为 `0 errors / 0 warnings`

### QD-05：依赖与第三方字节码

状态：`completed`

范围：

- `GradleDependency`：10
- `NewerVersionAvailable`：9
- dependency `TrustAllX509TrustManager`：9

实施要求：

- 先确认官方稳定版本、迁移说明、Android/Java 要求、ABI 和许可证
- compile SDK、Kotlin、MediaPipe、Filament、ONNX Runtime、Junrar、Jsoup 以及引入
  MINA/BouncyCastle/POI 的直接依赖分别处理，不做无证据的版本数字替换
- 每组升级独立执行依赖解析、编译、测试、native/DEX/资源和 APK 审计
- 第三方字节码警告只能通过替换、升级或移除真实依赖解决；禁止关闭 dependency lint

完成信号：current-only dependency/update/security 记录为 `0`，锁文件和依赖图可复现。

QD-05A 已完成：

- 删除生产与测试源码均无 import、反射标识或调用的 `tasks-text 0.10.35`，并同步移除
  Open Source Licenses 中不再成立的 MediaPipe 条目；不实施无消费者的 1.0.0 跨主版本迁移
- Filament `1.69.2 -> 1.74.0`、ONNX Runtime `1.27.0 -> 1.28.0`、Junrar
  `7.5.5 -> 8.0.0`、Jsoup `1.16.2 -> 1.23.1`
- Filament 1.74 把透明 clear color 的数组合同改为 `DoubleArray`；项目只把四个 RGBA 零值
  从 `floatArrayOf` 改为 `doubleArrayOf`，不改变透明合成或相机行为
- 依赖解析、完整 `:app:compileDebugKotlin` 与
  `MathMlPlainTextConverterTest` 3/3 通过；fresh lint 为
  `0 errors / 21 warnings / 1 baseline hint`，本批 7 条版本记录归零且没有新增类别
- GitHub `v1.74.1` 标签没有对应 Android Maven 制品，实际仓库解析证据确认可用版本为
  `1.74.0`；该结论由 Gradle 的全部配置仓库逐项 404 和随后成功解析共同证明

QD-05B/QD-05C 已完成：

- POI `5.2.3 -> 5.5.1`、BouncyCastle `1.78 -> 1.85`、MINA
  `2.1.6 -> 2.2.9`、Commons IO `2.13.0 -> 2.22.0`；两个 Commons Compress
  坐标统一为 `1.28.0`
- 根工程、8 个 Android library module、terminal 与 Android project template 的
  compile SDK 统一为 `37`；根工程和模板 Kotlin 统一为 `2.4.10`，target SDK 保持 `34`
- Room `2.8.4` 的处理器传递解析到 `kotlin-metadata-jvm:2.2.0`，无法读取 Kotlin
  metadata `2.4.0`；KAPT classpath 显式对齐到 `2.4.10` 后，Room 与 ObjectBox 代码生成、
  `:app:kaptDebugKotlin` 和 `:app:compileDebugKotlin` 通过，未改变 Room runtime、schema
  或数据库行为
- app 的 `sanitizePoiOoxml` 与 `sanitizeBcpkix`、terminal 的 `sanitizeMinaCore`
  均使用固定非传递输入，要求预期不安全入口存在，扫描保留 class 的字节码引用闭包，
  删除封闭且无消费者的 trust-all 能力、JPMS descriptor 和失效 JAR 签名，并在输出端
  再断言残留为 0；任务使用可复现顺序与固定时间戳且保留许可证/NOTICE
- 最终净化制品为：`poi-ooxml-safe-5.5.1.jar` `1924579` bytes、SHA-256
  `64C6482650587734E6F0BD2557126DEF4EC116AD00423EC9B2A3FEC551335ABE`；
  `bcpkix-safe-1.85.jar` `1186899` bytes、SHA-256
  `116333E91D90CA1AE5817546A646C5BF02515B5D70AF2E1CB99D4C4B49EB9428`；
  `mina-core-safe-2.2.9.jar` `697778` bytes、SHA-256
  `3134009683AD1B71B6B98091B31FDC0F9A6426DD1899B3CC2CAC4DA3111B2585`
- MINA sanitizer 的唯一 owner 已移入直接使用 FTPServer 的 KiyoriTerminalCore；
  terminal AAR 只包含 `libs/mina-core-safe-2.2.9.jar`，不再解析原始
  `mina-core:2.1.6`。子模块 `main@f75a10783b29ca158674b48486321ae26a4d264c`
  已推送并与远端一致
- final `:app:lintDebug` 用时 46 秒并通过，输出 `Lint found no new issues`；
  temporary/full baseline 与已审阅 baseline 交集为
  `retained=5776 / stale=0 / current-only=0`，只删除 7 条已升级版本记录和
  3 条已失效版本目录记录，没有吸收新问题或新增 suppress
- 完整 CI Python `166/166`、architecture `36/36 phase=m03`、formal readiness 和
  Android JVM `137 suites / 822 tests / 0 failures / 0 errors / 0 skipped` 通过；
  fresh-clone checker 已确认提交基线 `aa6d700c`，QD-05 父提交需在创建后重新验证
- `:terminal:assembleDebug` 与 `:app:assembleDebug` 通过；父构建为
  `236 actionable tasks / 39 executed / 197 up-to-date` 并执行
  `verifyDebugPlayerRuntimePackaging`
- APK 为 `463542133` bytes，SHA-256
  `C20E8BC3F8779EEC3802DAD2D8DE71374841CAD2C05A56EFA0ED5DAEA2A378B1`；
  package/version/min/target/compile SDK 为
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，Debug v2 签名与
  `zipalign -c -P 16 -v 4` 通过
- APK 内保留唯一 `META-INF/LICENSE.md`，其 `1171` bytes 与 BouncyCastle 1.85
  三个输入的逐字节 SHA-256
  `0E01F1549C9022F406392AC2947D32223B7C2E977D21EA2F8C182FDEB4DAE5FD`
  一致；四个被删除的不安全类/包字节串在 APK DEX/JAR entry 中均为 0
- 首次全量 DEX desugar 仍报告 `app/libs/arsc.jar` 缺少非线性控制流 stack-map，
  native strip 仍报告 `libsudo.so` 不是有效 object file；两者不属于 QD-05 依赖
  current-only Lint，但已进入下一警报批次，不能作为无警报构建封板

### QD-06：历史 baseline 高风险债务

状态：`completed`

current-only 归零后，优先审计 baseline 中项目自有的：

- 权限、receiver、API level 和格式化正确性
- TLS、证书、SSL error、network security configuration
- JavaScript interface 与 WebView 安全
- exported component、系统权限和硬件标识
- accessibility click contract
- native executable 与组件兼容边界

第三方源码、稳定兼容入口和明确产品能力必须分别记录 owner 和风险。项目自有且可修复的
高风险项不得继续仅依赖 baseline；无法在本地静态环境证明的项保持
`verification_pending`，但必须有明确的设备或发布验收条件。

完成信号：

- 高风险 baseline 项均有已修复或独立验收记录
- baseline 只删除已修复/失效项，不新增记录
- 生成新的 baseline 结构化统计和 SHA-256

验证证据：

- 删除 trust-all TLS owner；所有 WebView SSL error 明确取消，WebView debug 只在
  `BuildConfig.DEBUG` 开启；file/content/mixed-content、network security 与 JavaScript
  owner 按现有产品能力收紧
- Debug 专用 receiver 移入 debug Manifest；四个 Debug receiver 与
  `ShowerBinderReceiver` 需要 `android.permission.DUMP`，Shower binder 同时校验 action、
  binder descriptor 与存活状态。`ExternalChatReceiver`、`WorkflowTaskerReceiver` 因稳定
  外部兼容入口保持公开，并进入设备/外部集成验收
- RenderX 1.0.0 的依赖 Manifest 错误发布示例 `LatexView` 为第二个 `MAIN/LAUNCHER`；
  恢复精确 `tools:node="remove"` 合并规则，并把 `MissingClass` 限定在该 selector-only
  节点。新增 `verifySingleDebugLauncher` 读取 AGP `MERGED_MANIFEST`，要求最终 Debug
  只能暴露 `MainActivity` 一个 launcher，避免再次出现两个相同桌面图标
- Shizuku listener 使用同一实例注册与移除；WebKit document-start、message 与
  audio-mute API 在实际延迟执行闭包内再次验证 feature；URI 持久权限只接受读/写 flag
- 72 条隐式 Locale 格式化改为显式合同；Composable 使用可观察的
  `LocalConfiguration.current.locales[0]`，后台/日志沿用系统 Locale，协议 URL 与颜色
  十六进制使用 `Locale.ROOT`
- PTY 删除私有 `FileDescriptor.descriptor` 反射，改用 `ParcelFileDescriptor` 明确持有；
  `exitValue()` 使用 `waitpid(WNOHANG)` 区分运行中、真实退出码和等待失败，并增加 4 个回归测试
- shell identity launcher 从源码 assets 预编译文件迁为 Gradle 生成资产；NDK 28/API 26、
  `-nostdlib++`、AArch64 ELF64、四个 `PT_LOAD=0x4000`、无源码路径、无
  `libc++_shared.so` 依赖均由构建任务失败优先验证
- Android cached 进程恢复时会集中补跑的两处 `scheduleAtFixedRate` 改为
  `scheduleWithFixedDelay`
- 最终完整 baseline：`retained=5606 / stale=92 / current-only=0`；删除项为
  `DefaultLocale=72`、`RequiresFeature=5`、`ExportedReceiver=5`、
  `ImplicitSamInstance=4`、`DiscouragedApi=2`，以及 `MissingClass`、
  `DiscouragedPrivateApi`、`WrongConstant`、`UnsafeNativeCodeLocation` 各 1
- baseline SHA-256：
  `BEC89B4BF52DE60D7E839336080878B03DB154A7DC072D3C1875BDF0DD1748D0`
- 保留项已分类：公开 receiver、target SDK 34、Android ID、用户明确 HTTP/localhost
  能力、六个受限 JavaScript WebView owner、设备/rootfs 路径和现有 Application/ViewModel
  生命周期 owner；它们不等同于本地静态验证已证明无风险，退出条件分别在正式开发和设备/
  发布清单中保留

## 本地最终验证证据

- architecture `37/37 phase=m03`、Python `174/174`、formal readiness、working-tree
  Markdown `0` 个新增断链、Lint baseline normalization 与 `git diff --check` 通过
- `:app:assembleDebug` 执行 `238 actionable tasks / 29 executed / 209 up-to-date`，
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过；未出现
  `arsc.jar` D8 或 `libsudo.so` strip 警报
- 最终 APK 为 `463718677` bytes，SHA-256
  `768CAE74E27352DEE038AF0C4C9F8EE5E3C2C97B017B0F3C2BCD3E92080AEF6D`
- APK 身份为 `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`；
  Debug v2、单 signer 与 `zipalign -c -P 16 -v 4` 通过
- `aapt` 只报告 `com.ai.assistance.operit.ui.main.MainActivity` 一条
  `launchable-activity`；APK 内 `LAUNCHER` category 为 1，`LatexView` 为 0
- APK 仅含 `arm64-v8a`；51 个 `.so` 无重复 basename。连同 `8016` bytes 的生成式
  launcher 共 52 个 AArch64 ELF64，全部 `PT_LOAD >= 0x4000`
- APK 中 `libsudo.so`、`arsc.jar`、旧 launcher 源路径、trust-all manager、
  `UnsafeModelSsl`、`sshpass -p`、`StrictHostKeyChecking=no` 和 `handler.proceed()` 标记均为 0
- 提交后的 fresh clone、远端 `main` ref 与工作树干净状态仍属于交付门禁；设备和 Release
  验收不由本地 Debug 证据替代

## Stage 4 启动门禁

只有满足以下条件才进入 Browser 所有权迁移：

1. current-only Lint 为 `0 errors / 0 warnings`；允许存在的唯一 hint 是尚未删除 baseline
   时的 `LintBaseline` 状态提示
2. current-only 修复没有扩大 baseline、suppress 或 lint disable
3. 项目自有高风险 baseline 项已完成审计和根因处理
4. 完整 Kotlin 重编译不含未审计警报；architecture、Python、JVM、
   formal/fresh-clone、Markdown 和
   `git diff --check` 通过
5. 规定 Debug APK 构建、身份、签名、16 KB 对齐、Manifest、ABI、native 单副本和
   敏感内容审计通过
6. 每个债务批次形成清晰提交；最终推送后本地 HEAD、`origin/main` 和远端
   `refs/heads/main` 一致

设备和 Release 验收不由本门禁隐式完成，继续分别记录。

## 提交和回滚

建议提交序列：

1. `docs(quality): define pre-browser debt gate`
2. `fix(lint): clear correctness and compose diagnostics`
3. `refactor(resources): remove verified dead resources and normalize assets`
4. `fix(platform): close Android and WebView contracts`
5. `build(deps): update audited dependencies`
6. `fix(security): retire high-risk lint baseline debt`
7. `docs(quality): seal debt cleanup and stage 4 readiness`

每个提交以前一提交为回滚点。禁止使用 reset、clean、checkout 或强制推送回滚用户工作。
