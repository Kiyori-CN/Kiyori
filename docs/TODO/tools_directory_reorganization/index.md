---
feature: tools_directory_reorganization
scope: assistance
status: in_progress
last_reviewed: 2026-09-06
---

# Tools 目录重整

## 范围与当前状态

ADB、ToolPkg、示例包、Shower、Compose DSL 与 native-ripgrep 的分类迁移已经实施。
2026-09-06 继续将字符串工具归为 `tools/localization`，将拼写错误的
`ci/script/repair_repo_enviroment/windows` 环境修复入口归为 `tools/environment`。
当前工具职责、命令和副作用以 [工具索引](../../../tools/README.md) 为准。

## 意图

按工具职责将根目录入口迁入已有或新增子目录，并更新脚本相对路径、代码、测试与开发文档。旧入口不保留。

## 预期结果

- `tools/` 根目录只保留目录与固定的公开安装脚本
- 每个工具入口与其源码、模板和说明位于同一职责目录；私密配置与运行状态必须忽略
- 仓库内所有调用使用新路径
- 不增加旧路径转发脚本

## 步骤

1. [分类布局与入口迁移](./1_CategoryLayoutAndEntrypoints.md)

本轮源码与命令验证统一记录在 [仓库规范化实施记录](../kiyori_architecture_refactor/23_repository_structure_and_build_logic.md)。
本专项只描述工具目录整理；未运行设备、真实服务、翻译或发布，不把入口归位等同这些操作验收。
