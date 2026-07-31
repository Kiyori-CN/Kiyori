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
last_reviewed: 2026-07-31
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
- 当前阶段只落方案；未经再次批准不修改业务源码

## 现状证据

源码基线为 `main@62464b05`，与 `origin/main` 一致。当前工作树只包含本方案文档差异；
业务源码没有改动。`terminal` gitlink 干净，`tools/hotbuild/OperitNightlyRelease`
保持未初始化状态。

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
和 Browser TODO 仍把长期 Browser 目录写在 `com.ai.assistance.operit` 下。本方案在
`ready_for_approval` 阶段不替代它们。

如果用户批准本方案 v3，第一项仓库变更是同步更新 `CONTEXT.md`、正式架构文档和相关 Browser /
Player TODO 的目录归属，然后才开始源码迁移。已有行为合同继续有效，包括单一 Browser Runtime、
单一 PlayerSession、人工 UI 与 AI 共用状态、无第二数据 owner，以及现有 Back、presentation
和 Surface lease 规则。

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
`OperitApplication -> KiyoriApplication` 纯改名，88 项门禁测试、771 项 JVM 单测、
Debug APK 和精确差异验证通过。M-01 保持独立本地提交，后续 G-00 加固另行提交并通过
bundle fresh-clone 的 88 项门禁回归。namespace、数据、协议、UI、功能、terminal 和设备
均未改变或操作；下一源码里程碑必须单独规划。
