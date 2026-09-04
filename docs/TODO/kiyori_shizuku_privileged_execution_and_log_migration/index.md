---
status: verification_pending
owner: Kiyori Android shell, system tools, and logging
---

# Shizuku 特权执行与日志命名迁移

## 任务目标

修复 Shizuku 服务正在运行且已授权时，`super_admin:shell` 仍由应用 UID
(`u0_a825` 等)执行的问题；让受保护的系统工具使用一条可观测、可验证的特权执行路径；
把应用主日志的用户可见文件 owner 从 `operit.log` 迁移为 `kiyori.log`，同时完整保留已有日志内容。

本专项针对尚未对外发布的 Kiyori 版本。可以清理用户可见的旧品牌文案和主日志 owner，
但不改变仍被数据、插件、AIDL、Intent、native 或上游互操作使用的 `Operit` 兼容标识。

## 已确认根因（2026-09-04）

```text
super_admin:shell
  -> Tools.System.shell
  -> ToolRegistration.execute_shell
  -> ToolGetter.getShellToolExecutor
  -> StandardShellToolExecutor
  -> AndroidShellExecutor (默认 STANDARD)
  -> StandardShellExecutor / Runtime.exec
```

现有 `DebuggerShellExecutor` 已通过 Shizuku `IShizukuService.newProcess` 创建 Shell
进程，但没有被 `super_admin:shell` 选择。权限页读取的是 Shizuku 的实时状态，却没有把授权
结果同步到执行等级，也没有统一清理执行器首选等级缓存。系统操作工具在注册时捕获实例，权限
变化后仍可能持有旧实现；`execute_intent` 与 `send_broadcast` 的标准实现直接调用应用
`Context` API，因此受保护广播必然以应用 UID 被拒绝。

当前 `KiyoriLogger` 的唯一写入 owner 已经收口，但常量仍为 `operit.log`；架构检查器、清理、
导出和崩溃诊断需要与新 owner 同步。

## 目标合同

### 特权 Shell

- 新增显式 `executePrivilegedShellCommand`（名称可按现有代码风格调整）作为
  `super_admin:shell` 和高权限系统工具的入口；普通 `executeShellCommand` 的既有调用语义不因本专项
  自动改变。
- 明确配置 `ROOT` 时使用 Root，明确配置 `DEBUGGER` 时使用 Shizuku；未配置首选等级时，
  只有显式特权入口才依据实时 Shizuku 服务和授权状态选择 Debugger。
- 显式选择 `STANDARD`、`ACCESSIBILITY`、`ADMIN` 的普通调用不改变身份。特权条件不满足时
  返回失败和完整诊断，不把应用 UID 的结果伪装成 Shell/Root，也不静默改变执行身份。
- 诊断至少包含配置等级、选定路线、服务/binder 存活、Shizuku UID、
  `checkSelfPermission()`、执行器可用性和具体失败原因；日志不得记录凭据或完整敏感命令参数。
- `id` 成功验收必须观察到 `uid=2000(shell)` 或 `uid=0(root)`，不能只看工具返回 `success=true`。

### Shizuku 状态与权限页

- `ShizukuAuthorizer.initialize()` 幂等注册 binder received/dead 监听，连接死亡时清理缓存；
  每次状态变化清理首选等级缓存并通知 UI/工具 owner。
- 权限请求成功后，启动页末页和“设置 → 更多功能 → 权限”共用动作保存
  `AndroidPermissionLevel.DEBUGGER` 并清理 `AndroidShellExecutor`/执行器缓存；失败只呈现真实原因。
- Manifest 增加 Android 11+ 包可见性查询 `moe.shizuku.privileged.api`，不更换现有
  Shizuku API/provider 版本（当前 Maven API/provider 为 `13.1.5`）。

### 系统工具与 Intent

- 系统操作工具每次执行依据当前能力解析，不能在注册阶段永久捕获旧权限实例。
- Debugger 系统命令（`settings`、`pm`、`am`、`dumpsys` 等）通过显式特权 Shell 入口，
  保留现有 `ToolResult` 数据结构和真实退出码。
- `execute_intent`、`send_broadcast` 的特权变体生成结构化 `am start`、`am broadcast`、
  `am startservice` 命令。action、URI、package、component、flags 和常见基本类型 extras
  必须分别安全 quoting；无法安全表达的值明确失败。解析 stdout/stderr，`Permission Denial`、
  `Error:` 或非零退出码不能被判定为成功。
- 非特权模式继续使用应用 Context API；一次特权失败不得静默改变为另一种执行身份。

### 日志 owner 迁移

- `KiyoriLogger` 新写入 owner 为 `files/logs/kiyori.log`。
- 首次解析时，若同目录存在 `operit.log`，在新文件写入成功后完整迁移旧内容并删除旧文件；
  迁移失败不得阻断启动或丢失旧文件，也不得长期双写。
- `resetLogFile()`、`getLogFile()`、Logcat 页面、导出、崩溃报告和清理都只把 `kiyori.log`
  作为当前 owner；架构 contract token、fixture、线程名和哈希快照同步更新。
- `com.ai.assistance.operit`、`com.ai.assistance.operit.terminal`、`com.operit.*`、
  `operit://`、`.operit/config.json`、`OPERIT_*`、ToolPkg/MCP/AIDL/native 文件名、上游归属
  与历史数据格式属于兼容边界，本专项不做机械替换。

## 分阶段计划

1. **P0 文档与基线 [DONE]**：更新本文件、`docs/TODO/README.md`、`CONTEXT.md`、`README.md`；
   核对 `main`、子模块、正式门禁和并行任务边界。
2. **P1 路由与状态 [DONE]**：实现特权 Shell route、Shizuku 连接快照、幂等监听和授权成功后的
   等级/缓存同步；先补纯 Kotlin/JVM 路由测试。
3. **P2 工具解析 [DONE]**：收口 `ToolGetter`/`ToolRegistration` 的实时 owner，迁移 Debugger
   系统工具和结构化 `am` helper；补 quoting、退出码和错误识别测试。
4. **P3 日志迁移 [DONE]**：实现一次性原子迁移 helper，更新所有日志消费者、架构检查器和 fixture；
   用临时目录覆盖新安装、旧文件、新旧同时存在、迁移失败、清理和导出场景。
5. **P4 集成验证 [DONE]**：运行定向 JVM/静态检查、`git diff --check`、formal readiness、
   fresh-clone 和规定的 Debug 构建；审计 APK、资源、敏感内容和子模块。
6. **P5 真机验收 [PENDING]**：在 vivo Android 16 arm64 上验证 Shizuku 服务/授权、`id`、`settings`、
   `pm`、`am`、`dumpsys`、受保护广播、Intent、日志迁移/清理/导出、冷启动和长时间运行。
7. **P6 本地交付 [DONE]**：本轮完成工作树、构建产物、凭据、私有日记与子模块审计并保留本地
   Debug APK。当前任务未授权提交或推送；只有用户在对应轮次明确授权后，才允许精确暂存、提交并
   推送 `main`，随后独立核对 local/tracking/remote 三方 ref。

## 验收矩阵

| 层级 | 必须证据 | 当前状态 |
| --- | --- | --- |
| 源码路由 | `super_admin:shell` 不再固定 `StandardShellToolExecutor` | 已实现并由路由测试覆盖 |
| JVM/静态 | 路由、权限快照、am quoting、日志迁移和架构合同测试 | 21 个 JVM 用例与 15 个 Python 用例通过 |
| 构建 | `:app:assembleDebug --no-daemon --console=plain`、APK 身份/签名/16 KiB 对齐 | 已通过，Debug APK 已核验 |
| 设备 | Shizuku `uid=2000/0`、受保护命令/广播真实成功 | `verification_pending` |
| 运行稳定性 | Android 16 冷启动、权限重连、日志迁移和长期运行 | `verification_pending` |

## 本地验证证据（2026-09-04）

- `AndroidIntentShellCommandTest` 8/8、`AndroidShellRouteSelectionTest` 7/7、
  `ShellCommandDiagnosticsTest` 2/2、`KiyoriLogFileMigrationTest` 4/4；同批次总计 21/21，
  零失败、零错误、零跳过。
- `ci.test.test_toolpkg_sync` 与 M05B 架构正反向 fixture 总计 15/15 通过；四份生成 JavaScript
  均通过 `node --check`，APK 内 `super_admin.js` 与 `file_converter.js` 逐字节匹配生产 assets。
- `check_formal_readiness.py --repository . --require-main` 与
  `check_fresh_clone.py --repository .` 通过。后者验证提交基线和子模块可克隆性，不替代对当前未提交
  工作树的编译与测试。
- 完整架构检查仍报告本专项开始前已有的 `ARCH013/008/025/026/027/040/042` 漂移；本专项新增的
  `ARCH043`/M05B 日志 owner 契约通过，未扩大旧 allowlist 或借本轮吸收无关漂移。
- `./gradlew :app:assembleDebug --no-daemon --console=plain` 成功；产物
  `app/build/outputs/apk/debug/app-debug.apk` 为 `com.kiyori`、`versionCode=45`、`versionName=0.1.0`、
  `arm64-v8a`，使用 Android Debug v2 签名，`zipalign -c -P 16 4` 通过；文件 475,565,568 字节，
  SHA-256 为 `780974BD4A8D62370C4E521B212CE4407877B094DD738E700170D4765A2A9968`。
- 敏感文件名审计命中的 `assets/jks.jks` 与 `assets/pkcs12.keystore` 是自 2025 年即被仓库跟踪、
  由 `KeyStoreHelper` 使用的既有内置资源；本专项没有修改或新增密钥、凭据、`.env`、Cookie 或日记文件。
  `terminal` 子模块保持 clean，夜间构建可选子模块仍未检出。

## 回滚与风险边界

- 源码切片前保留当前 `main@2b8d481e3c747dc3979b5f1ad8a78a27b1d75c6d` 作为只读基线；不重置、
  不清理并行工作，不修改 `terminal` 或可选 `tools/hotbuild/OperitNightlyRelease` 子模块。
- 日志迁移只删除已确认完成复制的新旧日志对；任何校验、写入或重命名失败均保留旧文件。
- 真机未连接前，不能把本地 Shizuku API 调用或 Debug APK 视为已获得特权身份。
