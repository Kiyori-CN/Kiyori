---
status: accepted_design
plan_version: 3
local_upstream_snapshot: 0921f749a087c4a52a2202abdf32491ae335dd41
last_reviewed: 2026-07-31
---

# Operit AI 上游同步策略

## 目标

继续吸收 Operit AI 的真实能力改进，同时避免上游应用壳、品牌和页面 owner 覆盖 Kiyori 产品架构。

当前已执行 `git fetch upstream --prune`，本地 `upstream/main` 快照为
`0921f749a087c4a52a2202abdf32491ae335dd41`，提交时间 `2026-07-31 03:10:34 +08:00`。
每个后续大领域开始前仍必须重新 fetch 并记录新的精确基线。

## 四类所有权区域

### A：Operit 镜像区

典型内容：

- AI provider 与对话 runtime
- 模型、记忆、工作流和工具
- ToolPkg、Skill、MCP 与市场协议
- AI 页面内部实现
- 上游 tests 与 docs 中对应实现

原则：

- 尽量保持路径和类型可被上游识别
- Kiyori 定制通过 adapter 或窄 patch 接入
- 同步时优先保留上游提交结构

### B：共同维护区

典型内容：

- `MainActivity`
- `OperitApp`
- `Theme.kt`
- AI 与 Kiyori Shell 的导航交界
- Manifest、Gradle、资源和设置入口

原则：

- 每次上游同步人工审阅
- 不自动选择任一侧整文件
- 上游功能变化按当前 owner 分流到 Kiyori app、integration 或 Operit AI

### C：Kiyori 产品区

典型内容：

- Kiyori App Shell
- Browser、Player、Files、Mini App、Kiyori Settings
- Kiyori design system
- product capability

原则：

- 上游应用壳和产品导航不直接覆盖
- 如果上游变化包含可复用能力，只移植能力或视觉改进
- Kiyori 产品行为以当前架构合同为准

### D：稳定兼容区

典型内容：

- URI scheme 与 Intent action
- ToolPkg/Java bridge FQCN
- 序列化模型
- AIDL 与 JNI
- 数据库、偏好和备份格式
- Android 稳定组件入口

原则：

- 上游同步和 Kiyori 重构都不得顺手改名
- 任何变化需要独立兼容设计和数据验证

## 计划中的机器可读清单

正式重构阶段新增一个机器可读 ownership 清单，例如：

```text
config/architecture/
└── package-ownership.toml
```

清单记录：

- path pattern
- owner
- sync mode
- compatibility reason
- allowed dependency targets
- required validation

CI 脚本读取同一清单，防止文档与实际门禁分离。该文件在方案批准后再创建。

## 同步工作流

每次同步按以下顺序：

1. 确认工作树干净、分支和 HEAD 明确。
2. `git fetch upstream --prune`。
3. 记录旧 upstream baseline、新 `upstream/main` 和 merge-base。
4. 生成上游变更路径清单，并按 A/B/C/D 分类。
5. 在独立本地同步分支处理，禁止推送。
6. A 区按上游实现合入，B 区逐文件解释，C 区只移植适用能力，D 区逐项验证稳定合同。
7. 运行 Operit AI 定向测试、Kiyori capability 测试、完整 JVM 和 Debug 构建。
8. 审阅 Manifest、数据字符串、FQCN、assets 和 examples。
9. 创建本地同步里程碑提交并更新 baseline 记录。

禁止使用整文件 `ours` 或 `theirs` 掩盖冲突，也不通过扩大 allowlist 跳过问题。

## 重构与同步的时间关系

每个大领域遵守：

```text
先同步上游
	-> 建立干净基线
	-> 完成一个重构里程碑
	-> 完整验证
	-> 再次模拟或执行上游同步
```

文件移动期间不夹入上游功能提交。这样 Git rename detection、差异审阅和故障定位才有意义。

## 降低长期冲突的方法

- Operit AI 镜像区不做无意义格式化
- Kiyori product code 尽早移出上游高频文件
- B 区文件逐步缩小，只保留装配和 adapter
- 新 Kiyori 功能不继续写入 `core/tools/defaultTool/`
- 公共能力以 capability 接口连接，不让 UI 互相导入
- 上游新增产品壳功能先判断 owner，不直接复制到 Kiyori Shell
- 重命名提交保持纯粹，方便 Git 识别移动历史

## 当前风险

本地 Kiyori 与 upstream 已分别前进，当前已刷新引用显示 `54 / 43` 的左右提交差异，
且有 85 个同文件修改重叠。最新 upstream 增量没有修改 M-01 的 Application、Manifest 或
Lint baseline 精确范围，但增加了 memory、settings、route、assets 和多语言重叠。
重构前不先建立所有权清单，会让未来每次同步重复讨论同一问题。

因此 upstream sync strategy 是架构重构的一部分，不是重构完成后的补充文档。
