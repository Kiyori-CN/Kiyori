---
status: debt_cleanup_in_progress
baseline_commit: 6b6493a0bfd12072116e45fb733d551fad13e32b
current_phase: qd-04-platform-contracts
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

质量清理初始报告包含 `317` 条未基线化记录。QD-03 与 baseline 精确求交完成后的
fresh full lint 报告包含 `59` 条 XML 记录：

| 严重级别 | 数量 | 说明 |
| --- | ---: | --- |
| Error | 0 | QD-01 已清除 22 条缺失翻译和 5 条 Compose 资源读取错误 |
| Warning | 58 | WebView feature、生命周期、平台合同和依赖问题 |
| Hint | 1 | 现有 baseline 状态提示 |

Gradle 控制台不把 `LintBaseline` 状态提示计入 actionable hint，因此同一次执行摘要为
`58 warnings`。本清单的结构化数量以
`app/build/reports/lint-results-debug.xml` 为准。

当前 `app/lint-baseline.xml` 另有 `5787` 条历史记录，完整 lint 汇总为
`1265 errors / 4372 warnings / 150 hints`。baseline SHA-256 为
`80691E34E07299ABAA58C5F19DBDB27FFE5DB65AF03DEA2514B8A2AC0CC4F9E4`。

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

状态：`in_progress`

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

### QD-07：Kotlin 编译器警报与弃用迁移

状态：`pending`

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

### QD-05：依赖与第三方字节码

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

### QD-06：历史 baseline 高风险债务

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
