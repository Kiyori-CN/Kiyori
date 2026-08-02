---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: accepted_design
document_type: architecture-refactor-plan
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
local_upstream_snapshot: 0921f749a087c4a52a2202abdf32491ae335dd41
backup_scope: repository_only
device_scope: excluded
approved_at: 2026-07-31
last_reviewed: 2026-08-02
---

# Kiyori 项目架构与 Operit 命名重构

## 当前结论

本项目适合重构，但不适合一次性重写、全局替换或立即拆分 Gradle 模块。

推荐采用双包根架构：

- `com.kiyori` 只承载 Kiyori 产品所有权明确的应用壳、产品功能、设计系统、平台能力与集成层
- `com.ai.assistance.operit` 保留 Operit AI 子系统、生态协议、序列化类型、外部反射入口、
  JNI/AIDL 和需要稳定类名的 Android 兼容入口
- 两个包根通过稳定 capability 合同和 `integration.operit` 适配层连接
- 任一浏览器、播放器、下载、设置或 AI 状态只允许一个 owner，迁移时只移动所有权，不复制实现

这不是品牌妥协。Android application ID 已是 `com.kiyori`，Kiyori 继续拥有产品身份；保留的
Operit 标识用于准确表达 AI 子系统来源、外部生态或运行时兼容合同。

## 已确认边界

- Kiyori 尚未发布
- 必须保留当前开发数据
- 后续继续同步 Operit AI
- 首阶段不修改 Gradle `namespace`
- terminal 暂不纳入
- 正式实施时允许本地安全分支、里程碑提交和 Git bundle
- 不推送远端
- 备份范围是 `D:\10_Project\Kiyori` 当前仓库、本地未提交文件、必要私有开发配置和基线 APK
- 不连接或操作手机、模拟器、ADB，也不把应用运行数据作为本轮源码重构前置条件
- 方案阶段已结束；当前已按批准范围完成 M-00、G-00、M-01、M-02、M-03 与
  M-04A1/M-04A2/M-04B1/M-04B2/M-04B3/M-04B4/M-04B5/M-04B6/M-04B7/M-04C/M-04D1/
  M-04D2/M-04D3/M-04D4/M-04D5/M-04D6/M-04D7/M-04D8/M-04D9/M-04E；M-04 已完成
  总封板；M-05A1、M-05A2、M-05A3 已完成 design/app/platform owner 迁移与本地总封板；
  M-05B、M-05C、M-05D、M-05E 已封板；M-05 design/theme/platform 阶段完成

## 现状证据

实施前源码基线为 `main@62464b05`，与 `origin/main` 一致；当前 `main` 已通过已审计的本地
里程碑提交向前推进，`origin/main` 仍未改变且没有执行推送。M-01 只在原包内完成
`OperitApplication -> KiyoriApplication` 纯改名，G-00 与后续加固门禁、测试和恢复证据已落地。
`terminal` gitlink 始终为 `8d5c2c22`，`tools/hotbuild/OperitNightlyRelease` 始终保持未初始化状态。

主应用在 `com.ai.assistance.operit` 下约有 1146 个 Kotlin/Java 文件，一级分布为：

```text
com/ai/assistance/operit/
├── ui/             534
├── core/           266
├── data/           130
├── api/             76
├── util/            76
├── services/        23
├── integrations/    17
├── plugins/         14
├── widget/           7
└── provider/         3
```

当前依赖不是单向分层。主要跨层导入包括 `ui -> data` 630、`ui -> core` 481、
`core -> data` 214、`data -> core` 36、`core -> ui` 35、`services -> ui` 14。
在这些环存在时立即拆 Gradle 模块，会把包重构变成编译系统、依赖注入和资源拆分的复合风险。

Kiyori 与已刷新 `upstream/main@0921f749` 自共同基线后有 85 个同文件修改重叠，热点包括
`OperitApp.kt`、`MainActivity.kt`、`Theme.kt`、AI 对话、设置、工作流、Markdown 和构建文件。
因此未来同步不能继续把整个旧包根视为同一种所有权。

## 候选方案比较

### 方案 A：全局改名并整体移动到 `com.kiyori`

不采用。

它会同时改变数千个 import、序列化 FQCN、JNI 导出符号、AIDL、WorkManager worker 类名、
Android 组件名、ToolPkg Java bridge、脚本示例和上游补丁路径。即使能够编译，也无法证明已有
开发数据、系统角色、定时任务和插件互操作没有损坏。

### 方案 B：只改显示品牌，源码永久留在旧结构

不采用。

它能减少短期冲突，但 Kiyori 产品壳、浏览器、播放器和设置会继续埋在 AI 工具与旧 UI 目录中，
新功能仍会加深职责混杂，无法从结构上控制未来债务。

### 方案 C：双包根、兼容岛和分阶段迁移

推荐采用。

它允许 Kiyori 产品代码获得清晰目录，同时保留 Operit AI 的上游可识别性和生态兼容标识。
迁移可以按功能域逐步进行，每个里程碑都能独立构建、验证、提交和回滚。

## 目标结构概览

```text
app/src/main/java/
├── com/kiyori/
│   ├── app/
│   ├── capability/
│   ├── design/
│   ├── feature/
│   ├── integration/
│   └── platform/
└── com/ai/assistance/operit/
    ├── api/
    ├── core/
    ├── data/
    ├── plugins/
    ├── services/
    ├── ui/
    └── compatibility entrypoints
```

详细目录与依赖方向见
[目标包结构与依赖规则](2_target_package_architecture.md)。

## 总体实施原则

1. 先增加特征测试和架构门禁，再移动文件。
2. 一个提交只处理一种风险：纯移动、符号重命名、依赖反转或行为调整不能混在同一提交。
3. 迁移使用移动和单一委派，不复制状态 owner，不保留第二实现。
4. 数据文件名、DataStore、SharedPreferences、数据库、备份格式、Intent action、URI scheme、
   ToolPkg ID、序列化类型名和 native 名称默认不变。
5. 每个里程碑从干净工作树开始，完成静态检查、针对性测试、Debug 构建、APK 核验和差异反查。
6. 大领域迁移前后各同步一次 Operit AI；文件移动过程中不夹入上游功能同步。
7. 任何需要改变数据、协议、Android 外部组件名或 namespace 的事项单独立项，不作为目录整理附带动作。

## 与现有正式架构文档的关系

当前 [Kiyori 产品壳与导航架构](../../doc-src/architecture/kiyori_product_shell_and_navigation.md)
和 Browser TODO 仍把长期 Browser 目录写在 `com.ai.assistance.operit` 下。方案在
`ready_for_approval` 阶段不替代它们；本轮实施也没有移动 Browser、Player 或设置源码。

方案批准后的第一阶段已完成权威文档同步、G-00 门禁和 M-01 原包改名；下一源码里程碑前仍需
按本方案更新对应 Browser / Player TODO 的当前所有权记录。已有行为合同继续有效，包括单一
Browser Runtime、单一 PlayerSession、人工 UI 与 AI 共用状态、无第二数据 owner，以及现有
Back、presentation 和 Surface lease 规则。

`refactor_building_sys` 中的 Feature 模块隔离仍是独立草稿。本方案只规定先消除包级依赖环，
不把该草稿中的 Gradle 模块拆分视为已批准工作。

## 计划文档

1. [当前架构与命名分类账](1_current_architecture_and_naming_ledger.md)
2. [目标包结构与依赖规则](2_target_package_architecture.md)
3. [分阶段迁移顺序](3_migration_sequence.md)
4. [Operit AI 上游同步策略](4_upstream_sync_strategy.md)
5. [开发数据、备份与回滚](5_data_backup_and_rollback.md)
6. [验证矩阵与批准门禁](6_validation_and_approval_gate.md)
7. [源码所有权与文件迁移矩阵](7_file_ownership_and_migration_matrix.md)
8. [兼容合同与稳定标识清单](8_compatibility_contract_inventory.md)
9. [工作区、基线与备份作战手册](9_workspace_preflight_and_backup_runbook.md)
10. [里程碑执行模板与首批规格](10_milestone_execution_template.md)
11. [风险登记与停止条件](11_risk_register_and_stop_conditions.md)
12. [最终批准与实施就绪清单](12_approval_and_implementation_readiness.md)
13. [架构门禁与机器可读所有权规范](13_architecture_guard_specification.md)
14. [验证命令目录](14_validation_command_catalog.md)
15. [M-01 Application 原包改名精确影响清单](15_m01_application_rename_exact_manifest.md)
16. [M-00 权威文档同步精确清单](16_m00_document_authority_update_manifest.md)
17. [M-02 Application 全局访问平台化精确清单](17_m02_application_platform_access_manifest.md)
18. [M-03 KiyoriApplication 包迁移精确清单](18_m03_application_package_move_manifest.md)
19. [M-04 根组合与 Shell 精确实施清单](19_m04_root_composition_and_shell_manifest.md)
20. [M-05 Design 与 Platform 精确实施清单](20_m05_design_and_platform_manifest.md)
21. [Stage 4 前质量债务与开发就绪精确清单](21_quality_debt_and_stage4_readiness_manifest.md)

## 已批准的设计决策

用户已于 2026-07-31 授权在最终深度检查通过后正式实施，以下六项均按方案 v3 执行：

1. 是否批准双包根方案作为后续重构的唯一总体方向。
2. 是否同意把 Gradle `namespace` 视为独立的可选最终阶段，而不是本次重构的完成条件。
3. 是否同意 Android 系统可能持久化的旧组件 FQCN 保留为小型兼容入口，真实实现迁入 Kiyori 包。
4. 是否批准纯本地仓库备份方案：仓库外工作树快照、私有配置允许清单、安全分支、Git bundle、
   基线 APK 和临时恢复校验；不复制整个构建目录，也不涉及任何设备。
5. 是否批准在第一应用源码里程碑前先完成 G-00：机器可读所有权、稳定合同 snapshot 和
   M-01 允许差异门禁。
6. 第一应用源码里程碑是否按精确影响清单只在原包内处理
   `OperitApplication -> KiyoriApplication`，不移动包路径，也不同时修改
   `MainActivity`、`OperitApp`、主题或任何功能域。

## v2 到 v3 的纠正

方案 v2 曾得到方向性批准，但在任何安全分支、bundle、提交或源码变更发生前，用户明确纠正：
“开发数据”只指当前项目文件夹的开发状态，不需要手机、模拟器或连接设备；随后要求继续完善方案。

因此：

- v2 没有进入实施
- v2 中把设备备份设为源码写入硬门禁的内容作废
- v3 以纯本地仓库备份代替设备备份
- v3 增加 G-00 与 M-01 精确影响清单
- v3 已于 2026-07-31 获得实施授权

## 当前状态

本方案 v3 已获批准。M-00 与 G-00 已完成并提交；M-01 已在原包内完成
`OperitApplication -> KiyoriApplication` 纯改名，102 项门禁测试、771 项 JVM 单测、
Debug APK 和精确差异验证通过。M-01 保持独立本地提交，后续 G-00 加固另行提交并通过
bundle fresh-clone 的 102 项门禁回归。M-02 已完成四个窄 platform 合同、13 个 concrete
Application 消费者替换、ARCH017、合同测试与完整验证；Python `107/107`、JVM
`773/773`、Debug APK、v2 签名、16 KB 对齐和 player native packaging 均通过。
M-03 已把唯一 Application 移到 `com.kiyori.app.KiyoriApplication`，以 ARCH018 锁定
package-only 迁移、43 个过渡 Operit import、Manifest 与当时 6 个 Lint 路径；Python
`109/109`、JVM `773/773`、Debug APK、v2 签名、16 KB 对齐和 player native packaging
均通过。后续 M-04 完整 lint 再生成证明其中 1 条 `AppBundleLocaleChanges` 已失效，
当前 ARCH018 锁定旧路径 0、新路径 5。M-04A1 已拆出 Operit host CompositionLocal
合同，M-04A2 已把唯一根组合纯移动
并改名为 `com.kiyori.app.KiyoriApp`；M-04B1 已把 8 个 AI route/stack policy 符号移入
`com.kiyori.integration.operit.navigation`。M-04B2 已把 Browser exit presentation
contract 移入 `com.kiyori.capability.browser.presentation`，并把唯一纯 Shell
state/back owner 移到 `com.kiyori.app.shell`；旧 state 路径已删除，root 的过渡 Operit
import 从 45 降到 35。ARCH022/ARCH023、Python `117/117`、JVM `773/773`、Debug APK、
v2 签名、16 KB 对齐和 player native packaging 均通过。M-04B3 已把唯一
`KiyoriAppShell` host 纯移动到 `com.kiyori.app.shell`；ARCH024 锁定规范化源码、17 个
Operit import、唯一 helper owner、root 和测试接线。Python `119/119`、JVM `773/773`
与 Debug APK 审计通过。M-04B4 已把唯一 Modal AI Drawer host 纯移动到
`com.kiyori.app.shell`；ARCH025 锁定规范化源码、13 个 Operit import、唯一 owner、
App Shell 挂载和测试接线。Python `121/121`、JVM `773/773` 与 Debug APK 审计通过。
M-04B5 已把 primary destination presentation 声明组提取到
`com.kiyori.app.shell.KiyoriPrimaryNavigation`；ARCH026、Python `123/123`、
JVM `773/773` 与 Debug APK 审计通过。M-04B6 已把 Software Home 完整声明组提取到
`com.kiyori.app.shell.KiyoriSoftwareHome`；ARCH027、Python `125/125`、
JVM `773/773` 与 Debug APK 审计通过，旧 `KiyoriShellPages.kt` 只剩 Browser Search
声明组。M-04B7 已将这组代码提取到
`com.kiyori.app.shell.KiyoriBrowserSearch`，删除旧路径；ARCH028、Python `127/127`、
JVM `773/773`、Debug APK 与制品审计通过，APK SHA-256 为
`F104A5778C17FA518350FA22420E1073DF1A5FDC0540BF0119C7D3CDD8BBD0BF`。
M-04C 已把 route catalog、唯一 PackageManager-backed
navigation revision、ToolPkg listener、gateway lifecycle 和 route-root helper 移入
`com.kiyori.integration.operit.navigation`；ARCH029、Python `129/129`、JVM `775/775`
与 Debug APK 审计通过，APK SHA-256 为
`71A860FF9957FCAAB290669449159F8269F145C6C904FB94E8E2E3E0942FC402`。M-04D1 已新增
唯一 `com.kiyori.app.startup.KiyoriMainPendingRequests`，把 13 个分散字段收口为一个
Activity 持有的状态 owner，并保持 request ID 不匹配时不消费、分享失败时只清文件而保留
文字、OAuth take-and-clear 和 current navigation 不随 shortcut 消费清除的既有语义。
ARCH030、Python `131/131` 与 3 条新 JVM 合同测试通过；完整 JVM、formal readiness 和
Debug APK 封板均通过。完整 JVM 为 `778/778`，APK SHA-256 为
`3C0FA7301EF846C90D24CCCB0A4A0492D3A2E7374F9B24182640B17A89423F96`。当前进入
M-04D2，仅提取纯 Intent decoder；namespace、数据、协议、UI 行为、功能、terminal 和
设备均未改变或操作。
M-04D2 已把所有 long/string/parcelable/data payload 读取、action 优先级、OAuth/route/
Browser/share 分类移动到 `com.kiyori.app.startup.KiyoriMainIntentDecoder`，并让
MainActivity 的 10 个公开 action/extra 常量继续桥接同一字面值。decoder 不改写 Intent，
不调用 runtime、Toast、日志、lifecycle 或 request-ID；ARCH031 与 Python `133/133`
通过。完整 JVM 为 `783/783`，Debug APK SHA-256 为
`E4F53F4646191A935453252D8B0ACF0093F46F1F609648B437462771018B411F`。当前进入 M-04D3
display coordinator。M-04D3 已把 sustained-performance、refresh-rate mode/rate 选择和
hardware acceleration 迁入无状态唯一 coordinator；ARCH032 与 Python `135/135` 通过，
完整 JVM 为 `786/786`，Debug APK SHA-256 为
`53038237D9B31087CBE6BB1172B7943E988F62809366AA3C2798C716EE848E16`。当前进入 M-04D4
shared-content coordinator。M-04D4 已把 pending→SharedFileHandler 转交迁入唯一
lifecycle-bound coordinator，两个状态 owner 均未复制；ARCH033 与 Python `137/137`
通过，完整 JVM 为 `789/789`，formal readiness、lint baseline normalization、
Markdown links、`git diff --check` 与 Debug APK 封板均通过；APK SHA-256 为
`AE11A63373B55A7B7A65F59C07C00886ECFEF1D23083CED92CC8D96C498DFC45`。当前进入
M-04D5。M-04D5 已把最近任务可见性恢复移入无状态
`KiyoriMainTaskVisibilityCoordinator`，原两个调用时机保持；ARCH034、Python
`139/139`、3 条纯策略测试、完整 JVM `792/792`、完整 architecture、formal readiness
与 Debug APK 封板均通过；APK SHA-256 为
`F246315DBCCF89522C7AB2C14EA96E67234A79D8F76B84A05F5352409AE743BB`。下一独立切片
M-04D6 已把方向状态、纯 reducer 与确认对话框迁入唯一
`KiyoriMainOrientationCoordinator`，Activity 保留 lifecycle dispatch、Plugin Loading
hide 顺序与 `recreate()`；ARCH035、4 条方向行为测试、完整 architecture、Python
`141/141`、完整 JVM `796/796`、formal readiness 与 Debug APK 封板均通过；APK
SHA-256 为 `8E8BA68316543E9685743C3C0D893CC74484E3C2243B0DA71F103195C4209149`。当前进入
M-04D7。M-04D7 已把 MainActivity 启动阶段 notification permission launcher、纯决策、
rationale/request/result 处理迁入唯一 `KiyoriMainNotificationPermissionCoordinator`，
并保持 onCreate 前注册时机；ARCH036、4 条纯决策测试、完整 architecture、Python
`144/144`、完整 JVM `800/800`、formal readiness、lint、Markdown、diff 与 Debug APK
封板均通过，APK SHA-256 为
`555D0222EC1DDF9B58C69E1A6EF4CE931CADD4BDCC32C1BC157B9A47625E0D0B`。本次完整 lint
再生成删除 `51` 条失效历史记录，保留 `5792` 条，不吸收 `328` 条 current-only 问题；
ARCH018 当前锁定 Application lint 路径旧 0、新 5。M-04D8 已把 agreement /
permission-guide / content 三态 resolver、唯一 `showPermissionGuide` UI 投影与启动门禁
presentation 迁入 `KiyoriMainStartupGateCoordinator`；两个既有 preference owner、
Agreement/Permission 页面实现、300/500 ms 时序、plugin loading 与 content host 均保持。
ARCH037 正反向夹具 2/2、定向 JVM 7/7、完整 architecture、Python `146/146`、完整 JVM
`807/807`、formal readiness、lint baseline normalization、255 个 working-tree Markdown
文件、`git diff --check` 与 Debug APK 封板均通过；APK 大小为 `470053617` bytes，
SHA-256 为 `1F5835C63B6946182E3F004EF5EC75CC930E9C8F54A380C67237E9587600DDDF`。
主机文件系统 mtime 报告未来值 `2026-08-02 00:53:05 +08:00`，与权威任务日期
`2026-08-01` 不一致，只作为本机时钟异常记录。M-04D9 已完成只读边界收敛、ARCH038
失败优先证据与最小生产实现：新增无持久状态的一次性 content request projection 和唯一
`KiyoriMainContentHost`，并让 MainActivity 只挂载一次该 host。
`PluginLoadingState`、`setContent`、主题、startup gate、插件启动、方向对话框、lifecycle
和 Android/runtime side effect 均保留原 owner。ARCH020、ARCH030、ARCH033 已同步迁移
到新的 content-host owner；ARCH019 至 ARCH038、完整 Python `148/148`、完整 JVM
`134 suites / 810/810`、formal readiness、lint baseline normalization、255 个
working-tree Markdown 文件、`git diff --check` 与 Debug APK 封板均通过。APK 大小为
`470058476` bytes，SHA-256 为
`A568EBDF498CD531E3E799BBD6A763E36269E6C1008A524F45B8E5B510ABE26F`。严格
`:app:lintDebug` 仍报告既有未基线化债务 `31 errors / 289 warnings / 8 hints`，但 D9
source/test current-only 为 0、MainActivity 仅有既有 locale warning，baseline 保持
5792 条、stale 0、current-only 328 且未吸收新问题。M-04D9 已封板。

M-04E 已完成。MainActivity 继续作为稳定 Android launcher 与
lifecycle/runtime side-effect 执行边界，不移动、不复制；精确 ownership record
`operit-main-activity-compatibility` 已替代唯一到期 ARCH001 例外。ARCH039 锁定精确
owner 字段、稳定 package/FQCN/Manifest MAIN/LAUNCHER、32 条项目 import、
`com.kiyori.feature` 零依赖、唯一 content host 和旧例外清零，并证明精确文件 record
覆盖宽泛目录 owner、重叠宽泛 glob 继续失败。E2 只处理 M-04 owner 内可证明行为不变的
API/Compose lint，严格 lint 从 `31 errors / 289 warnings / 8 hints` 收口到
`27 errors / 287 warnings / 2 hints`；M-04 errors/hints 为 0，两条剩余 warning 分别转入
M-05 design/platform 和明确的 bundle/locale 发布策略。lint baseline 结构化交集为
`5792 retained / 0 stale / 316 current-only`，SHA-256 不变。

M-04 总封板通过 ARCH019 至 ARCH039、完整 Python `152/152`、完整 JVM
`134 suites / 810/810`、formal readiness、255 个 working-tree Markdown 文件、
`git diff --check` 与 Debug APK 审计。APK 为 `468983182` bytes，SHA-256
`81F6BA9436031AB20CBFB30C23F111A927FF0913D422A4BC602BBDD1CDEC38D5`；
package/version/label、SDK、Application、稳定 launcher、多进程、arm64 native、
player packaging、Debug v2 签名和 16 KB 对齐均保持。当前进入 M-05：先完成
design/theme/platform 的精确 ownership 清单、行为合同、风险与失败优先门禁，再做最小
owner 迁移；不整体移动 `util`，不复制主题偏好、权限事实或生命周期状态。

M-05 只读审计已确认旧 `ui/theme` 的纯 design、preference host、system-bar、AI 字体和
glass 必须拆分。M-05A1 已完成三个纯 `com.kiyori.design.theme` owner、11 个生产消费者、
偏好 adapter、测试拆分、ownership 许可和三份 SHA snapshot；旧 Browser/Settings theme
路径已删除。ARCH040 已取得缺少 `KiyoriColorSchemes.kt` 的 failure-first 证据，正反向
fixture 与真实实现 gate 通过。M-05A1 批准的两条 Kiyori design import 使 M-04B App Shell
规范化 hash 与完整项目 import snapshot 发生预期变化；ARCH024 输入校验已同步为项目根
约束并继续精确比较全部 import。封板通过完整 architecture、Python `154/154`、JVM
`135 suites / 810 tests`、formal readiness、lint `5792/0/316`、A1 影响文件 0 issues、
256 个 working-tree Markdown、diff、规定 Debug 构建和 APK 静态审计。APK 为
`471291682` bytes，SHA-256
`B8CD99D49D3F93745282C4E04F7FD7C158A875BE8897FF17161EC4D2C7F93646`；身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、零重复 basename、Debug v2 和
16 KB 对齐全部保持。M-05A1 已封板。M-05A2 已把纯 enum/data/color resolver 与 Compose
`MaterialTheme` adapter 拆为两个 `com.kiyori.design.theme` owner，锁定全部浅深色值、
枚举顺序、稳定 ID 映射、底栏 `#FFC153`、天气 `#C57C00/#FFD166`、`0.5f` luminance
阈值、58 个消费者、102 条 import 与测试所有权；旧
`ui/theme/KiyoriSemanticTheme.kt` 已删除，不保留 compatibility facade。M-04B AI Drawer、
Primary Navigation 与 Software Home 的精确项目 import/hash snapshot 随批准的 owner 迁移
同步。ARCH025/026/027/040/041、完整 architecture、Python `156/156`、JVM
`135 suites / 810 tests`、formal/fresh-clone readiness 通过。新鲜 full lint 暴露的
`27 errors / 287 warnings / 2 hints` 均不位于 A2 实际改动行；两个新 design 文件 0 命中，
baseline 交集保持 `5792/0/316`。规定 Debug 构建与 APK 静态审计通过，APK 为
`471292358` bytes，SHA-256
`60D613A5F9BFBD019296E21E05547DF16B0789FCDE73FF2699AFBAC415A8BBC0`；身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、零重复 basename、Debug v2 与
16 KB 对齐全部保持。M-05A2 已封板。M-05A3 已新增 pure design
`KiyoriTheme/KiyoriTypography`、app preference/font/Glass host 与 platform Application
system-bar owner，删除旧 `Theme.kt`、`OperitTheme`、`Theme.Operit` 和旧 root-theme test。
固定 Typography 与纯 `applyFontFamilyToTypography` 已迁入 design；Type 的配置字体、文件读取、
日志与 AI 局部字体适配保持原 owner。Liquid/Water Glass 算法与 PlayerActivity fullscreen
system-bar 不迁不改；M-05A3 历史封板 hash 为
`AEF88E8F34DD08098D858E4E5D3F36CBF1867AE36E6C44B96346F6A0BC11A756`，QD-04 当前
ARCH042 hash 为 `0958C96D76C5C30E98EA84F08AC29CA576FA263C497AD1976BFEF9B4E326B7CA`。
ARCH040/041/042、
完整 architecture `phase=m03`、Python `158/158`、JVM `134 suites / 810 tests`、
formal/fresh-clone readiness、fresh lint 影响面和 Debug APK 审计通过；baseline 交集为
`5792/0/316`。APK 为 `477957302` bytes，SHA-256
`9373518AEB8FA8BD2C02DCFDE5741833653A2D76AFE270D2C27F4D2BF88C0074`，身份、SDK、组件、
arm64 53 native、Debug v2 和 16 KB 对齐保持。M-05A3 已封板。M-05B 已新增唯一
`com.kiyori.platform.logging.KiyoriLogger` 与 `KiyoriLogTextFormatter`，把旧 AppLogger
收口为无状态兼容 facade，并让 8 个 Kiyori app owner 直接使用 platform logger；Operit
消费者、Provider bind、日志导出和两个 static-mock 合同保持旧入口。ARCH043
failure-first、正反向 fixture、真实 gate、ownership 与 ARCH018/020/032～037 已通过，
完整 architecture `phase=m03`、Python `160/160`、JVM
`135 suites / 813 tests`、formal/fresh-clone readiness、Markdown 和 diff 通过。full lint
保持既有 `27 errors / 287 warnings / 2 hints`；新 logging owner 为 0 命中，baseline
只删除迁移后失效的旧 `AppLogger.kt` `StaticFieldLeak`，结果为 `5791/0/316`。规定
Debug 构建和 APK 静态审计通过，APK 为 `477957302` bytes，SHA-256
`3A9BCBC711DB1FB735C3828AA1751F80B347C8C1FC7E34E783F16FFD96D631A1`；身份、SDK、
组件、多进程、arm64 53 native、10 个播放器目标库、Debug v2 与 16 KB 对齐保持。M-05B
已封板。M-05C 已把 callback 注册、current Activity 弱引用、activity/started count 与
foreground boolean 收口到唯一 `KiyoriActivityLifecycle`，把 keep-screen-on、plugin、
External Chat、microphone、PlayerCrash、VirtualDisplay 与 Shower 收口到唯一 Operit
integration；旧 `ActivityLifecycleManager` 完整 ABI 与 13 个 FQCN consumer 保持。ARCH044
failure-first、正反向 fixture、真实/完整 architecture、Python `162/162`、JVM
`136 suites / 817 tests`、readiness、fresh lint 影响面、Markdown、diff 与 Debug APK
审计通过。fresh full lint 保持既有 `27 errors / 287 warnings / 2 hints`，M-05C 四条路径
为 0 命中。APK 为 `477957302` bytes，SHA-256
`3792DD58C87D1DDD4F977BB8CD4B4407458EB911EC17CB0CB48CB8876184D2D3`，身份、SDK、
组件、多进程、arm64 53 native、10 个播放器目标库、Debug v2 与 16 KB 对齐保持。M-05C
已封板。M-05D 已新增唯一
`com.kiyori.platform.permission.KiyoriNotificationPermissionCapability`，把 API 33、
system grant/rationale、RequestPermission launcher 与 action resolver 从 app coordinator
迁入 platform；`OperitNotificationPermissionResources` 只桥接原两个 `R.string` ID。旧
`KiyoriMainNotificationPermissionCoordinator` 保持一参数构造、`checkAndRequest()`、
MainActivity 早注册/单调用、6 条日志和 2 个 Toast，且不再直接 import `R` 或系统权限 API；
其到期 ARCH004 exception 已删除。ARCH045 failure-first、ARCH036/045 正反向 fixture、
真实/完整 architecture、生产 Kotlin 编译、4 条 platform policy tests 与实施前后精确
javap、完整 Python `164/164`、完整 JVM `136 suites / 817 tests`、formal/fresh-clone
readiness、Markdown、diff、lint baseline normalization 与敏感签名扫描均通过。fresh full
lint 保持既有 `27 errors / 287 warnings / 2 hints`，四条 M-05D 路径为 0 命中。规定 Debug
构建和 APK 审计通过：APK 为 `477957302` bytes，SHA-256
`BB370BC2602880CA4DCE488F67DA4AB9F105C1CFF07A0E4D2FF31A3D3CC38B34`，身份、SDK、
组件、多进程、arm64 53 native、10 个播放器目标库、Debug v2 与 16 KB 对齐保持。M-05D
已封板。M-05E 已新增唯一 `KiyoriPaths` 与纯 `KiyoriBackupPaths`，旧
`OperitPaths` / `OperitBackupDirs` 保留完整 ABI 纯委派；目录、大小写、层级、plugin ID、
raw snapshot、备份格式和旧布局扫描保持。ARCH046、最终完整 architecture、Python
`166/166`、JVM `137 suites / 822 tests`、readiness、fresh lint、Markdown、diff、敏感审计
与 Debug APK 审计通过。APK 为 `477957302` bytes，SHA-256
`E6A5E78CFB4441399DC36D83583BF6F441441E3736A85A17759695D73ACF790C`。M-05E 已封板，
M-05 design/theme/platform 阶段完成。精确路径、消费者、非目标、
ARCH040/ARCH041/ARCH042/ARCH043/ARCH044/ARCH045/ARCH046、风险和验证见
[M-05 Design 与 Platform 精确实施清单](20_m05_design_and_platform_manifest.md)。

M-02 至 M-05 已形成 checkpoint
`6b6493a0bfd12072116e45fb733d551fad13e32b`，fresh clone 与 formal readiness 均通过。
当前先执行
[Stage 4 前质量债务与开发就绪精确清单](21_quality_debt_and_stage4_readiness_manifest.md)：
把 `317` 条 current-only Lint 按正确性、行为保持型现代化、资源、Android/Browser
平台合同和依赖五批收口，并审计当前 `5786` 条历史 baseline 中项目自有的高风险项。完成该
质量门禁后再进入阶段 4 Browser 产品域。
