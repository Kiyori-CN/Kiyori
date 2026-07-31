---
status: accepted_design
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-07-31
---

# M-00 权威文档同步精确清单

## 目的

M-00 只更新“当前和未来应该怎样组织”的权威文档，不能把历史实现记录批量改写成尚未存在的路径。

当前仓库中大量 Browser、Player、启动性能和 ADR 文档记录了当时真实文件、符号、测试和设备证据。
这些内容属于历史证据，不是当前目标包树。全局替换会破坏可追溯性。

## 文档职责分类

### A：M-00 必须更新

#### `CONTEXT.md`

新增当前架构重构合同：

- Kiyori 产品根为 `com.kiyori`
- Operit AI 与兼容岛保留 `com.ai.assistance.operit`
- capability 与 `integration.operit` 是唯一跨根连接层
- 当前代码尚未完成迁移，文档不得把目标路径写成已实现事实
- Browser Runtime、PlayerSession、下载、userscript 和设置继续保持单一 owner
- namespace 首阶段不变
- terminal 排除

不得重写现有 Browser、Player、Shell、数据和 UI 行为合同。

#### `docs/doc-src/architecture/kiyori_product_shell_and_navigation.md`

新增“目标包所有权与迁移状态”章节：

- 当前 Shell 行为合同保持不变
- 长期目标路径迁入 `com.kiyori.app`
- AI route 和 ToolPkg screen registry 留在 Operit AI
- route 转换进入 `integration.operit.navigation`
- M-00 只记录方向，不声称文件已移动

不得修改 Pager、Back、抽屉、AI Home、window inset、presentation 或视觉合同。

#### `docs/TODO/README.md`

更新：

- 方案版本
- 当前状态
- 方案文档链接
- 本轮明确非目标

不得改写其他已完成任务的历史证据。

#### `docs/TODO/kiyori_architecture_refactor/`

更新：

- `status: accepted_design`
- 精确批准日期和 baseline
- 已批准/未批准边界
- M-00、G-00、M-01 状态

计划状态更新不能把尚未实现的 G-00 或 M-01 标为完成。

### B：M-00 只允许增加 superseding note

#### `docs/TODO/kiyori_browser_product_completion/index.md`

如果其长期目录归属会与方案 v3 冲突，只在顶部增加：

- Browser 行为合同继续有效
- 未来源码所有权由方案 v3 接管
- 历史路径和已完成证据不追溯改写

#### `docs/TODO/kiyori_startup_performance/index.md`

只允许说明：

- 当前基线类仍为 `OperitApplication`
- G-00 后 M-01 计划原包改名
- 启动顺序和性能结论不因命名计划变化

在 M-01 实际完成前，不把正文中的当前类名预先改成 `KiyoriApplication`。

### C：历史证据，M-00 禁止改写

包括但不限于：

```text
docs/TODO/kiyori_browser_product_completion/1_*.md
docs/TODO/kiyori_browser_product_completion/2_*.md
...
docs/TODO/kiyori_browser_product_completion/12_*.md
docs/doc-src/decisions/*.md
历史 release、build、device、bugfix 和验收记录
```

这些文档中的：

- `OperitApplication`
- `OperitApp`
- `OperitTheme`
- `ui/main`
- `ui/features/player`
- `core/tools/defaultTool/websession`
- 当时的测试数量、APK hash 和设备结果

必须保留为历史事实。只有文件已真实迁移且文档明确承担当前入口职责时，才在对应里程碑更新。

## M-00 允许差异

允许：

- 方案版本和状态
- 当前/目标架构区别
- 新的相对链接
- 迁移顺序、批准边界和非目标
- superseding note

禁止：

- 修改业务源码、Manifest、资源、Gradle、AIDL 或 native
- 把目标路径描述为已经存在
- 改写历史测试、APK、设备或问题证据
- 改变 Browser、Player、Shell、数据或 UI 行为合同
- 批量替换 `Operit*`
- 修改 terminal 或未初始化 hotbuild gitlink

## M-00 验证

1. `git diff --name-only` 只包含允许文档。
2. `rg` 确认新增内容区分 current 与 target。
3. 历史证据文件 hash 不变。
4. Markdown 相对链接通过。
5. `CONTEXT.md` 与正式架构文档不存在互相矛盾的 owner。
6. formal readiness 通过。
7. Debug APK 构建通过。
8. APK hash 可以与文档修改前相同；无源码变化时不能要求 hash 必须变化。

## M-00 提交边界

推荐单独提交：

```text
docs(architecture): accept Kiyori refactor plan v3
```

提交前必须确认：

- staged allowlist 只有本清单 A/B 类文档
- 没有 APK、bundle、备份、私有配置或任务日记
- 没有源码、terminal 或 submodule 变化
- v3 已获得用户精确批准

M-00 提交完成后才能创建包含 accepted design 的最终 Git bundle，并进入 G-00。
