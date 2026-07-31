---
status: accepted_design
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-07-31
---

# 最终批准与实施就绪清单

## 方案封版目标

“万事俱备”不是指已经修改源码，而是指正式实施前的每个问题都有：

- 明确答案，或
- 明确负责人和批准点，或
- 明确停止条件与恢复路径

没有任何关键问题依赖临场猜测。

## 已确认事项

| 事项 | 当前结论 | 证据/载体 |
| --- | --- | --- |
| 产品定位 | Kiyori 是全能浏览器，Operit AI 是子系统 | `CONTEXT.md` |
| 发布状态 | Kiyori 未发布 | formal readiness / CONTEXT |
| 开发数据 | 必须保留 | 用户确认；backup runbook |
| 上游同步 | 继续同步 Operit AI | upstream sync strategy |
| namespace | 首阶段不改 | 用户确认；contract inventory |
| terminal | 暂不纳入 | 用户确认；EXCLUDE 规则 |
| Git | 允许本地安全分支、里程碑提交、bundle，不推送 | 用户确认 |
| 工作区 | 只使用当前仓库，不创建长期副本或 worktree | 用户最新确认；workspace runbook |
| 备份范围 | 当前仓库、未提交文件、私有开发配置和基线 APK | 用户最新确认；不涉及设备 |
| 首个安全门禁 | G-00 架构所有权与稳定合同基线 | architecture guard specification |
| 第一应用源码批次 | 原包内 `OperitApplication -> KiyoriApplication` | M-01 exact manifest |

## 已获用户批准

### A：总体架构

- [x] 批准双包根：`com.kiyori` 产品根 + `com.ai.assistance.operit` Operit 兼容岛
- [x] 批准 capability 与 `integration.operit` 作为唯一连接层
- [x] 批准先包边界、后 Gradle 模块化

### B：兼容策略

- [x] 批准旧 Android 组件和 WorkManager worker FQCN 作为稳定兼容入口
- [x] 批准 JNI/AIDL、协议、数据和生态标识不随目录重构改名
- [x] 批准 namespace 作为独立后续 RFC，而非本轮完成条件

### C：当前文件夹保护

- [x] 批准仓库外保存 tracked patch 和未跟踪方案文档
- [x] 批准按允许清单复制实际存在且被 Git 忽略的私有开发配置
- [x] 批准创建并验证原始基线安全分支
- [x] 批准创建 Git bundle，并从 bundle 临时克隆和执行 `git fsck`
- [x] 批准保存并核验基线 Debug APK
- [x] 确认手机、模拟器、ADB 和应用运行数据不属于本轮范围

### D：执行工作区

- [x] 继续在 `D:\10_Project\Kiyori` 工作
- [x] 不创建第二个长期源码目录或 Git worktree
- [x] 仓库外目录只用于备份和临时恢复校验，不用于继续开发

### E：G-00 与第一应用源码里程碑

- [x] 先建立通用 ownership、stable contract snapshot 和架构门禁
- [x] G-00 不修改 Android 运行时代码
- [x] 只改 `OperitApplication` 类名和文件名
- [x] 保留原包路径
- [x] 不改 `MainActivity`、`OperitApp`、主题、数据、协议、namespace 和 terminal
- [x] M-01 只触及精确影响清单允许的 16 个实现文件和相称文档

## 实施前最终门禁

开始第一源码里程碑前，必须按顺序完成：

1. 用户批准本方案 v3
2. 更新 `CONTEXT.md`、正式架构文档和相关 TODO 的目录归属
3. 冻结 upstream baseline
4. 仓库外保存当前 tracked patch、未跟踪文档和私有配置允许清单
5. 建立指向原始 HEAD 的本地安全分支
6. 创建 M-00 文档提交
7. 创建并验证 Git bundle
8. 从 bundle 临时克隆并运行 `git fsck --full`
9. 构建、复制并核验基线 APK
10. 实现并验证 G-00
11. 运行 formal readiness
12. 确认工作树只包含已批准的方案/基线变化
13. 在任务日记中记录可恢复点

任何一步失败，都停在准备阶段。

## 第一里程碑交付条件

第一批完成后必须同时满足：

- 业务逻辑 diff 只包含命名、package/import、Manifest 精确引用和注释
- application ID、namespace、version、数据名称和协议零变化
- KiyoriApplication 可由 Manifest 加载
- main/crash/repair/player 多进程启动行为保持
- Application 相关测试和 Kotlin compile 通过
- Debug APK 身份、签名、ABI 和 native packaging 通过
- 当前文件夹备份、bundle 临时恢复和基线 APK 证据仍完整
- 本地里程碑提交已创建且未推送

## 用户批准文本模板

用户可以直接回复：

```text
批准 Kiyori Operit 架构重构方案 v3。
按双包根、兼容岛、单一状态 owner、先包边界后模块化执行。
先完成 G-00 架构所有权与稳定合同门禁，不修改 Android 运行时代码。
第一应用源码里程碑按精确影响清单，只在原包内将
OperitApplication 改名为 KiyoriApplication，
不移动包路径，不改 namespace、数据、协议、UI、功能或 terminal。
正式实施前允许创建本地安全分支、Git bundle、里程碑提交和基线 APK；
不推送。只保护 D:\10_Project\Kiyori 当前文件夹的开发状态，
不使用手机、模拟器或 ADB。
```

## 当前实施状态

- 方案 v3：已批准
- 安全分支：已创建
- 未提交工作树快照：已创建并校验
- M-00：进行中
- Git bundle：等待 M-00 提交
- G-00：尚未开始
- M-01：尚未开始
- 推送：禁止

方案文档可以继续修订，但每次改变总体架构、兼容策略、数据范围或第一里程碑，都必须递增
`plan_version` 并重新请求批准。
