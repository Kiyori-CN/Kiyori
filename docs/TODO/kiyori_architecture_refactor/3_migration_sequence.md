---
status: accepted_design
plan_version: 3
last_reviewed: 2026-07-31
---

# 分阶段迁移顺序

## 总规则

- 每个里程碑只处理一个结构问题
- 每个里程碑形成独立本地提交
- 每个里程碑完成 Debug APK 构建和核验
- 上一个里程碑验证通过后才能开始下一个
- 任何行为变化都从纯移动提交中拆出
- 不把 namespace、数据迁移、依赖升级、UI 改版或 terminal 混入

## 阶段 0：方案确认

当前阶段。

交付：

- 当前架构与命名分类账
- 目标包树与依赖规则
- 迁移批次
- 上游同步策略
- 数据保护、备份与回滚
- 验证矩阵

完成条件：

- 用户明确批准总体方案
- 用户明确批准第一源码里程碑
- 当前文件夹备份、私有配置保护和恢复验证方式达成一致

批准后、源码迁移前先把 `CONTEXT.md`、正式架构文档和相关领域 TODO 的目录目标同步为
本方案 v3。该文档里程碑只改变架构描述，不改变已经验收的运行时与 UI 合同。

## 阶段 1：正式实施基线与安全点

不改源码。

步骤：

1. 确认 `main`、HEAD、`origin/main`、子模块和工作树状态。
2. 更新本地 `upstream/main` 引用并记录精确 commit。
3. 运行 formal readiness、完整 JVM 基线和 Debug 构建。
4. 核验 APK package、版本、ABI、签名、16 KB 对齐、Manifest 和 SHA-256。
5. 创建指向基线 HEAD 的本地安全分支，但继续在 `main` 工作。
6. 在仓库外保存 tracked patch、未跟踪文档和允许清单中的私有开发配置。
7. 完成 M-00 文档提交。
8. 创建 `--all` Git bundle 并执行 `git bundle verify`。
9. 从 bundle 临时克隆并运行 `git fsck --full`。
10. 保存并核验基线 APK。

停止条件：

- 工作树不干净且差异来源不明
- baseline 测试或构建失败
- 未提交工作树或私有配置无法按清单保护
- 安全分支不指向原始基线
- bundle、临时克隆或 `git fsck` 无法验证
- 基线 APK 复制前后 hash 不一致

## 阶段 1.5：G-00 架构与稳定合同门禁基线

不修改 Android 运行时代码。

交付：

- `config/architecture/package-ownership.toml`
- stable identifiers、Manifest component、persistence、native/IPC snapshot
- 通用 `check_architecture_boundaries.py`
- 对应 Python 单元测试
- M-01 允许文件、规范化纯改名和 Lint baseline 约束

G-00 必须复用同一套 ownership 与 snapshot，不建立只服务一次改名的临时检查器。

完成条件：

- 当前源码基线通过
- 每条 error 规则至少有一个失败测试
- Windows/Linux 路径处理一致
- 不读取 `.env` 或私密配置
- 不修改源码或生成输出
- 能准确拒绝超出
  [M-01 精确影响清单](15_m01_application_rename_exact_manifest.md) 的候选差异

## 阶段 2：应用根命名与包边界

### 里程碑 2.1：Application 根类

推荐作为 G-00 之后的第一应用源码里程碑。

只做：

- 在原包 `com.ai.assistance.operit.core.application` 内把
  `OperitApplication` 重命名为 `KiyoriApplication`
- 更新 Manifest 与直接引用
- 更新日志 TAG 和注释
- 同步对应测试或静态检查
- 严格遵守
  [M-01 Application 原包改名精确影响清单](15_m01_application_rename_exact_manifest.md)

不做：

- 不移动 Application 包路径
- 不拆启动逻辑
- 不移动 `MainActivity`
- 不移动 `OperitApp`
- 不改变 WorkManager、JSON、ImageLoader、数据库或初始化顺序

这样可以先消除错误的产品名，同时避免 Operit AI 子系统立刻反向依赖 `com.kiyori.app`。

### 里程碑 2.2：Application 全局访问收口与包迁移

先建立稳定的 `com.kiyori.platform` 合同，逐项替代对 Application 具体类型的访问：

- application context
- 全局 JSON
- app startup time
- 主进程初始化请求

确认 `com.ai.assistance.operit` 不再导入 Kiyori app 具体类后，才把
`KiyoriApplication` 移到 `com.kiyori.app`。

这一步不改变初始化阶段、线程、顺序、异常处理或多进程行为。

### 里程碑 2.3：根 Composable

只做：

- `OperitApp` 重命名为 `KiyoriApp`
- CompositionLocal 拆到职责明确的文件
- Kiyori Shell 继续使用同一状态 owner 和 route

不做：

- 不调整导航行为
- 不改变 Pager、Back、抽屉、AI Home 挂载或主题

### 里程碑 2.4：Kiyori App Shell

按文件而不是按旧目录整体迁移：

- `KiyoriAppShell`
- `KiyoriShellState`
- Kiyori 首页、负一屏、设置宿主和产品导航
- 产品级 route model

保留在 Operit AI：

- AI screen registry
- ToolPkg 动态页面
- AI 对话路由
- Operit AI 页面状态

建立 `integration.operit.navigation` 作为唯一连接点。

### 里程碑 2.5：MainActivity 责任拆分

先增加特征测试，再把以下职责从 Activity 提取：

- 外部 Intent 解析
- 启动门禁状态
- Kiyori 内容装配
- 显示与系统栏策略

是否移动 `MainActivity` FQCN，取决于 Android 稳定组件策略确认。即使保留旧入口，真实 UI 逻辑也应迁入
Kiyori app 包。

## 阶段 3：设计系统与平台能力

### 里程碑 3.1：Kiyori 主题命名

- `OperitTheme -> KiyoriTheme`
- `Theme.Operit -> Theme.Kiyori`
- Kiyori semantic、browser、settings theme 归入 `design/theme`

必须保持颜色、Typography、Shapes、动态状态和 AI 局部个性化结果不变。

### 里程碑 3.2：平台服务

按职责迁移日志、生命周期、权限和路径类。禁止把整个 `util` 目录改名。

`OperitPaths` 和 `OperitBackupDirs` 的类名可改，但路径字符串、文件名、目录结构和 raw snapshot
格式保持不变。

## 阶段 4：Browser 产品域

顺序固定：

1. 为现有 Browser Runtime 建立 capability 接口和特征测试。
2. 迁移 domain 模型和纯策略。
3. 迁移 history、bookmark、download、settings、userscript 的唯一数据 owner。
4. 迁移 WebSession registry、WebView 生命周期和 presentation。
5. 迁移 Browser UI。
6. 把 Operit AI browser tools 改为唯一 capability adapter。
7. 清理旧路径中已经没有所有权的实现。

每一步必须确认：

- 没有第二 WebView
- 没有第二 session registry
- 没有第二下载任务数据库
- 没有第二 userscript repository
- 人工 UI 与 AI 工具看到相同活动会话

Browser 是整个重构风险最高的领域之一，不与 Player 同批。

## 阶段 5：Player 产品域

顺序固定：

1. 冻结 PlayerSession、Surface lease、media request 和设置行为的特征测试。
2. 迁移 domain 与 presentation。
3. 迁移 UI。
4. 迁移主进程 runtime owner。
5. 保持 AIDL、`:player` service 和 native 名称稳定。
6. 建立 Operit AI 到 Player capability 的 adapter。

任何 AIDL 包或 service FQCN 迁移都在独立后续里程碑进行，并要求真机播放与进程崩溃隔离验收。

## 阶段 6：设置、文件、备份与其他产品域

按独立里程碑迁移：

- Kiyori Settings
- Files
- Mini App
- Backup 与 Recovery
- Download 产品设置
- Android system surfaces

设置页只导航到领域 owner，不复制偏好。未实现的小程序和文件能力不借用 Operit 包管理或 AI 工具页面。

## 阶段 7：Operit AI 集成收口

目标：

- Kiyori product code 不直接穿透到 Operit AI 内部 ViewModel 或 Composable
- Operit AI 不直接依赖 Kiyori feature UI
- `integration.operit` 成为宿主、路由和 capability 适配的唯一位置
- 旧包根只保留 Operit AI、生态、协议和稳定入口

本阶段再审计剩余 `Operit*` 标识：

- 产品所有权名称全部迁移
- 兼容和生态名称留下理由
- 历史归属和许可证不改写

## 阶段 8：依赖门禁与模块化决策

先增加 CI 静态门禁：

- 禁止 Operit AI 导入 Kiyori feature UI
- 禁止 capability 导入实现
- 禁止 feature 间直接访问内部 owner
- 禁止新增未分类的 `Operit*` 产品标识
- 禁止 Manifest、脚本和 assets 中出现未登记的硬编码 FQCN

依赖环归零后，再决定是否建立：

- `:core:capability-api`
- `:feature:browser`
- `:feature:player`
- 其他有唯一 native/resource owner 的模块

模块化不是本轮包重构的强制完成条件。

## 阶段 9：可选 namespace 与组件名迁移

这是独立 RFC，不默认执行。

需要同时解决：

- `R` 与 `BuildConfig` 的生成包
- 全部 Manifest 相对类名
- AIDL、JNI、ProGuard 与测试 runner
- Android 系统保存的组件名
- 上游 Operit import 冲突
- 已安装开发数据和系统角色复测

如果收益不足以覆盖同步成本，Gradle `namespace` 可以长期保留为兼容 build namespace。
