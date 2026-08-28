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

已完成。当前实施位置是阶段 4 Browser 产品域。

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
- stable identifiers、Manifest contract、persistence、native/IPC 与
  AIDL/Room/ObjectBox critical-file hash snapshot
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

### 里程碑 2.2：Application 全局访问收口（M-02）

先建立稳定的 `com.kiyori.platform` 合同，逐项替代对 Application 具体类型的访问：

- application context
- 全局 JSON
- app startup time
- 主进程初始化请求

本里程碑只移除 Operit AI 对 `KiyoriApplication` concrete class 的直接依赖。
在 M-02 完成状态中，`KiyoriApplication` 仍留在原包，并作为主进程初始化接口的唯一真实实现。

这一步不改变初始化阶段、线程、顺序、异常处理或多进程行为。

精确文件、合同、测试与停止条件见
[M-02 Application 全局访问平台化精确清单](17_m02_application_platform_access_manifest.md)。

### 里程碑 2.3：Application 包迁移（M-03）

只有 M-02 的旧包 concrete dependency 清零并完整验证后，才把：

```text
com.ai.assistance.operit.core.application.KiyoriApplication
-> com.kiyori.app.KiyoriApplication
```

本里程碑只处理 Application 文件与 Manifest/测试/ProGuard 的精确包路径变化，不再次修改
四类 platform 合同、初始化逻辑、数据、协议或 UI。

M-03 已完成：唯一 Application 位于 `com.kiyori.app.KiyoriApplication`，原同包
`ActivityLifecycleManager` 依赖改为显式 import，ARCH018 锁定方法体、43 个过渡 Operit
import、Manifest 和 Lint 路径。旧 Application 路径与旧运行时 FQCN 均已清除。

### 里程碑 2.4：根 Composable

已完成：

- `OperitApp` 重命名为 `KiyoriApp`
- CompositionLocal 拆到职责明确的文件
- Kiyori Shell 继续使用同一状态 owner 和 route
- ARCH019/ARCH020 锁定唯一 CompositionLocal owner、根组合纯移动、唯一 host 与旧符号清理

本里程碑未做：

- 不调整导航行为
- 不改变 Pager、Back、抽屉、AI Home 挂载或主题

### 里程碑 2.5：Kiyori App Shell

M-04A 至 M-04E 已完成并通过总封板；后续 M-05 design/theme/platform 也已完成。当前进入
阶段 4 Browser 产品域。

M-04B1 已把 AI route/stack policy 移入 `integration.operit.navigation`；M-04B2 已把
Browser exit presentation contract 与唯一纯 Shell state/back owner 移入 Kiyori 包；
M-04B3 已把 `KiyoriAppShell` host 纯移动到 `com.kiyori.app.shell`，保持同一 Pager、
Back、drawer 和 state owner；M-04B4 已把 Modal AI Drawer host 纯移动到同一 Shell
owner；M-04B5 已把 primary destination visual、root dispatch 与 bottom navigation
提取到同一 Shell owner；M-04B6 已把 Software Home 完整声明组提取到
`com.kiyori.app.shell.KiyoriSoftwareHome`。当前 M-04B7 只提取 residual Browser Search
声明组；M-04B7 已将其移入 `com.kiyori.app.shell.KiyoriBrowserSearch`，删除旧混合文件，
不改写 Browser Runtime、history、profile 或搜索行为。当前进入 M-04C，开始收口 Operit
navigation integration。M-04C 已将 route catalog、唯一 PackageManager-backed navigation
revision、ToolPkg listener、gateway lifecycle 与 route-root helper 移入
`com.kiyori.integration.operit.navigation`。M-04D1 已把 MainActivity 的 pending
shared/browser/OAuth/shortcut/route/Shell request state 收口到唯一
`com.kiyori.app.startup.KiyoriMainPendingRequests`；当前继续 M-04D remaining internal
host。M-04D2 已进一步把 Intent payload 读取和 action 优先级提取到无副作用
`KiyoriMainIntentDecoder`，Activity 继续执行全部 Android/runtime side effect。M-04D3
已把窗口性能、刷新率和硬件加速配置提取到无状态 `KiyoriMainDisplayCoordinator`。
M-04D4 已把 pending shared files/text 转交提取到生命周期绑定的
`KiyoriMainSharedContentCoordinator`，不移动 `SharedFileHandler` StateFlow owner。
M-04D5 至 M-04D9 已依次完成 task visibility、orientation、notification permission、
startup gate 与 content host 拆分。M-04E 已在不移动 Activity 或新增 host 的前提下删除
到期例外、锁定稳定兼容入口，并完成 M-04 owner 内的行为保持型质量收口。

按文件而不是按旧目录整体迁移：

- `KiyoriAppShell`（已完成）
- `KiyoriShellState`（已完成）
- Kiyori 首页、负一屏、设置宿主和产品导航
- 产品级 route model

保留在 Operit AI：

- AI screen registry
- ToolPkg 动态页面
- AI 对话路由
- Operit AI 页面状态

建立 `integration.operit.navigation` 作为唯一连接点。

### 里程碑 2.6：MainActivity 责任拆分

先增加特征测试，再把以下职责从 Activity 提取：

- [DONE M-04D1] 待处理外部请求状态与 requestId 精确消费
- [DONE M-04D2] 外部 Intent 纯解析、稳定 action/extra 常量合同和密封 command
- [DONE M-04D8] 协议/权限级别/内容三态启动门禁与唯一 UI 投影
- [DONE M-04D9] Kiyori 内容装配：一次性 request projection、shared-content
  交接、`LocalPluginLoadingState` provider 与唯一 `KiyoriApp` 挂载
- [DONE M-04D3] 显示性能与刷新率策略
- [DONE M-04D4] pending external files/text 转交与清理
- [DONE M-04D5] 最近任务可见性恢复与 AI foreground-runtime 判定
- [DONE M-04D6] 方向变化状态、确认对话框与 Activity recreate 边界
- [DONE M-04D7] MainActivity 启动通知权限 launcher/request/result owner
- [DONE M-04D8] 权限级别与协议接受后的启动门禁
- [DONE M-04E] 删除已到期的 MainActivity ARCH001 文件例外，以精确路径 ownership record
  表达稳定 Android 兼容入口；精确 owner 覆盖宽泛目录 owner，重叠宽泛 glob 仍失败
- [DONE M-04E] ARCH039 锁定 Activity package/FQCN、Manifest launcher、精确项目 imports、
  `com.kiyori.feature` 零依赖、唯一 content host 和旧例外清零
- [DONE M-04E] 只清理 M-04 owner 内可证明不改变行为的 lint；其余项目债务留在对应后续阶段，
  不扩大 baseline

application system-bar/edge-to-edge owner 已确认位于主题实现，Player 全屏 system-bar owner
位于 PlayerActivity；二者分别留给 design/platform 与 Player 领域迁移，不在 MainActivity
责任拆分中制造平行 owner。

`MainActivity` FQCN 作为稳定 Android 组件入口保持不变；内部实现按职责迁入 Kiyori app
包。Intent 解析、时间戳生成和 Android 副作用不得进入纯 pending-request owner。

## 阶段 3：设计系统与平台能力

本阶段已完成。M-05 按精确文件与 owner 清单、视觉/系统行为特征测试和失败优先架构门禁，
完成最小迁移；主题命名、platform owner 迁移、行为调整和全量 `util` 整理没有混为一批。

精确子里程碑：

1. M-05A1：纯 ColorScheme、Browser theme、Settings theme
2. M-05A2：Kiyori semantic design
3. M-05A3：root theme/style 命名、preference host 与 system-bar owner
4. M-05B：platform logging
5. M-05C：platform lifecycle
6. M-05D：Android permission capability
7. M-05E：paths/storage

M-05A1 已封板：纯 ColorScheme、Browser theme 与 Settings theme 由
`com.kiyori.design.theme` 唯一拥有，旧 preference resolver 只保留偏好决策并委派 design
resolver，旧 Browser/Settings theme 路径已删除。ARCH040 failure-first、正反向 fixture、
消费者/ownership/test gate、完整 architecture、全量 Python/JVM、formal readiness、
lint、Markdown、规定 Debug APK 与制品审计全部通过；设备/UI 仍待验证。M-05A2 也已封板：
纯 semantic color contract 与 Compose MaterialTheme adapter 已分离后迁入
`com.kiyori.design.theme`，当前 59 个消费者、102 条 import、测试所有权和三份受影响 M-04B
snapshot 均由 ARCH041 与 ARCH025/026/027 精确锁定；颜色、映射和 UI 未改变。完整
architecture、Python `156/156`、JVM `810/810`、formal/fresh-clone、范围内 lint、规定
Debug APK 与制品审计通过；仓库范围 full lint 仍保留 316 个既有 current-only issue，
不属于 A2 改动。M-05A3 也已封板：旧 root theme 已拆成 pure design
`KiyoriTheme/KiyoriTypography`、app preference/font/Glass host 与 platform Application
system-bar owner；两个生产调用者复用唯一 app host，`Theme.Operit` 的 6 个声明和 6 个
Manifest 引用已精确改名为 `Theme.Kiyori`。Type 的配置字体与 AI 局部适配、Liquid/Water
Glass 算法和 Player fullscreen system-bar 均保持；OperitUtilityTheme、
FloatingWindowTheme 与三个纯字体应用消费者只迁移到 design import。ARCH042 与完整
architecture、Python `158/158`、JVM `810/810`、formal/fresh-clone、范围内 lint、规定
Debug APK 与制品审计通过。M-05B 也已封板：唯一
`com.kiyori.platform.logging.KiyoriLogger` 持有 executor/提前解析的内部 filesDir/file/switch
状态，进程 Context 继续只由 `ApplicationContextAccess` 持有，旧
`com.ai.assistance.operit.util.AppLogger` 保留无状态兼容 facade，日志 formatter 迁入 platform，
8 个 Kiyori app owner 直接使用新 logger，Operit 消费者保持旧入口。ARCH043 failure-first、
正反向 fixture、真实 gate、ARCH018/020/032～037、完整 architecture、Python `160/160`、
JVM `135 suites / 813 tests`、formal/fresh-clone、working-tree Markdown 和
`git diff --check` 均通过。新鲜 full lint 保持既有 `27 errors / 287 warnings / 2 hints`；
logger/formatter/test 三个新 owner 为 0 命中，结构化 baseline 只删除迁移后失效的旧
`AppLogger.kt` `StaticFieldLeak`，结果为 `5791 retained / 0 stale / 316 current-only`。
规定 Debug 构建和 APK 审计通过；设备与多进程文件写入仍待验证。M-05C 已完成唯一
`KiyoriActivityLifecycle` facts/callback owner、Operit side-effect owner 与旧完整 ABI facade
实现；ARCH044 failure-first、正反向 fixture、真实/完整 architecture、Python `162/162`、
JVM `136 suites / 817 tests`、formal/fresh-clone、fresh lint 影响面、Markdown、diff、
规定 Debug APK 与制品审计全部通过。fresh full lint 保持既有
`27 errors / 287 warnings / 2 hints`，M-05C 四条路径为 0 命中。APK 为 `477957302`
bytes，SHA-256 `3792DD58C87D1DDD4F977BB8CD4B4407458EB911EC17CB0CB48CB8876184D2D3`，
身份、SDK、Application、稳定 launcher、多进程、arm64 53 native、10 个播放器目标库、
Debug v2 与 16 KB 对齐保持。M-05C 已封板。M-05D 已完成 production owner：platform
capability 唯一持有 API 33、system grant/rationale、RequestPermission launcher 与 action
resolver；无状态 Operit bridge 唯一映射 denied/rationale 资源 ID；旧 app coordinator
只保留原日志/Toast projection。旧 coordinator 的一参数构造与 `checkAndRequest()` javap
精确保持，MainActivity 不改早注册/单调用，`AndroidPermissionPreferences` 与两个非启动
通知检查不改。ARCH045 failure-first、ARCH036/045 正反向 fixture、真实/完整 architecture、
生产编译、完整 Python `164/164`、完整 JVM `136 suites / 817 tests`、readiness、Markdown、
diff、lint 影响面、规定 Debug 构建与 APK 审计通过；fresh full lint 保持既有
`27 errors / 287 warnings / 2 hints` 且 M-05D 四条路径为 0 命中。APK 为 `477957302`
bytes，SHA-256 `BB370BC2602880CA4DCE488F67DA4AB9F105C1CFF07A0E4D2FF31A3D3CC38B34`。
M-05D 已封板。M-05E 已把路径计算收口到唯一 `KiyoriPaths`，以纯
`KiyoriBackupPaths` 投影备份目录，并保留旧 `OperitPaths` / `OperitBackupDirs` 完整 ABI
兼容 facade；ARCH046、Python `166/166`、JVM `137 suites / 822 tests`、readiness、
fresh lint、Markdown、diff、规定 Debug 构建和 APK 审计均通过。M-05E 已封板，阶段 3
design/theme/platform 完成；阶段 4 Browser 产品域尚未开始。
精确范围见
[M-05 Design 与 Platform 精确实施清单](20_m05_design_and_platform_manifest.md)。

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

当前阶段。开始 Browser owner 迁移前，先完成
[Stage 4 前质量债务与开发就绪精确清单](21_quality_debt_and_stage4_readiness_manifest.md)。
该门禁要求 current-only Lint errors/warnings 归零、项目自有高风险 baseline 债务完成
审计、完整自动检查和规定 Debug APK 通过；不得以 suppress、扩大 baseline 或关闭检查
替代修复。

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
