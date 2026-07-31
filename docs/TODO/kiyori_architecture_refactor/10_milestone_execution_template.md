---
status: accepted_design
plan_version: 3
last_reviewed: 2026-07-31
---

# 里程碑执行模板与首批规格

## 里程碑不可变模板

每个正式重构里程碑都必须复制以下字段到对应 TODO 或提交说明：

```text
Milestone ID:
Title:
Owner:
Plan version:
Baseline commit:
Upstream baseline:
Scope:
Non-goals:
Source paths:
Target paths:
Rename map:
Stable contracts touched:
State owners:
Upstream sync class:
Risk IDs:
Rollback point:
Required commands:
Required tests:
Required APK audit:
Device acceptance:
Commit boundary:
Open questions:
```

## 里程碑生命周期

### M-00：方案与权威文档封版

范围：

- 用户批准方案 v3
- 更新 `CONTEXT.md`、正式架构文档和相关领域 TODO 的目录归属
- 不移动源码、不改变行为合同

验证：

- 文档链接、格式、术语和目录树一致
- formal readiness PASS
- 工作树只有文档差异

### G-00：架构与稳定合同门禁基线

范围：

- 创建机器可读 package ownership
- 创建 Manifest、persistence、native/IPC 和 stable identifier snapshot
- 实现通用 architecture boundary checker 及其单元测试
- 接入 M-01 精确允许文件与规范化纯改名检查

明确不做：

- 不修改 `app/src/main/java`、Manifest、资源、AIDL 或 native
- 不改 Gradle namespace、依赖版本或模块结构
- 不生成需要提交的构建产物
- 不读取 `.env`、`local.properties` 或其他私有配置内容

完成条件：

- 当前基线通过
- 违规样例测试能稳定失败
- Windows/Linux 路径一致
- 诊断包含规则 code、文件和原因
- 不为 M-01 建立一次性检查器

### M-01：Application 原包内纯改名

范围：

- `core/application/OperitApplication.kt`
- Manifest Application class
- 13 个 Kotlin 消费者
- 6 个 Lint baseline 文件路径
- 注释、TAG 和类型名

精确范围以
[M-01 Application 原包改名精确影响清单](15_m01_application_rename_exact_manifest.md)
为准：14 个 Kotlin 文件、42 次 Kotlin 出现、1 次 Manifest 出现、6 次 Lint baseline 路径，
共 16 个实现文件和 49 次出现。

目标：

```text
OperitApplication -> KiyoriApplication
```

保持：

- 包路径仍为 `com.ai.assistance.operit.core.application`
- Application ID、namespace、初始化顺序、多进程行为和全局 JSON 不变
- WorkManager、数据库、ImageLoader、服务启动和日志行为不变

明确不做：

- 不移动 `MainActivity`
- 不移动 `OperitApp`
- 不建立 platform context
- 不改 `OperitPaths`
- 不改主题和 UI

纯改名验收：

- `git diff --summary --find-renames=90%`
- 旧符号仅在迁移说明或兼容清单中出现
- 新符号引用闭合
- Manifest 能解析新 Application class
- Application/启动相关定向测试
- Debug APK 与 baseline 身份一致

### M-02：Application 全局访问平台化

前置：

- M-01 完成
- 新增访问合同的特征测试

范围：

- application context
- 全局 JSON
- app startup time
- 主进程初始化请求

目标：

- Operit AI 代码不直接依赖 Kiyori app 具体类
- 只依赖稳定 `platform` 或 capability 合同
- 不改变初始化顺序、线程、锁、异常和多进程分支

禁止：

- 新建第二 Application singleton
- 把平台合同变成 Service Locator
- 在本批顺手移动 UI 或数据类

### M-03：Application 移入 Kiyori app

前置：

- M-02 的旧包依赖清零
- Manifest、ProGuard、测试和多进程检查完成

目标：

```text
com.ai.assistance.operit.core.application.KiyoriApplication
-> com.kiyori.app.KiyoriApplication
```

验证：

- 所有 Operit AI 引用只经过 platform/capability/integration
- 旧包中没有同名实现
- main、crash、repair、player 进程行为没有变化

### M-04：Kiyori Root Composition 与 Shell

顺序：

1. `OperitApp -> KiyoriApp`
2. CompositionLocal 按职责拆分
3. Kiyori Shell 迁入 `com.kiyori.app.shell`
4. AI route 与 ToolPkg registry 迁入 integration
5. MainActivity 内部 host 提取

每一步都不改变：

- Pager 页序
- Back 优先级
- AI Home 稳定挂载
- 抽屉手势合同
- window inset
- route ID 和 ToolPkg registration order

### M-05 及以后：按领域单独执行

领域顺序：

```text
design/platform
    -> browser capability/runtime/ui
    -> player capability/ui/runtime
    -> settings/files/backup
    -> Operit integration cleanup
    -> dependency gates
    -> optional namespace RFC
```

Browser 和 Player 不在同一里程碑；数据合同和 Android 组件合同不作为目录整理附带变更。

## 每个里程碑的执行顺序

1. 记录基线 commit、upstream baseline 和工作树
2. 运行最窄的 preflight
3. 写入/更新特征测试
4. 执行纯移动或纯改名
5. 执行编译和针对性测试
6. 审查差异纯度和旧符号残留
7. 运行完整 Debug build
8. 核验 APK、Manifest、协议和数据合同
9. 当前纯本地范围不操作设备；需要设备的未来行为里程碑单独标记 `verification_pending`
10. 更新文档和日记
11. 创建本地里程碑提交
12. 重新打 bundle 或记录新的恢复点

## 不能合并的变化

以下变化不得出现在同一个提交：

- package move 与业务逻辑重写
- class rename 与数据迁移
- UI 重新设计与目录整理
- namespace 迁移与普通 feature move
- upstream sync 与大规模 rename
- AIDL/JNI 改动与普通 Kotlin move
- 依赖升级与架构边界迁移

## 里程碑完成判定

只有同时满足以下条件才能进入下一批：

- 源码差异在预期路径内
- 旧 owner 不再写入新 owner 的状态
- 兼容入口仍可解析
- 相关测试全部通过
- Debug APK 已核验
- 当前授权范围内的验证完成；设备证据需求单独记为 `verification_pending`
- 回滚点已保存
- 文档与任务日记已更新
