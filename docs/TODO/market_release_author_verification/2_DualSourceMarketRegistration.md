# 双来源市场登记

## 历史实现

选本地制品后总是在当前用户的 OperitForge 仓库创建或更新资产。市场表已有 GitHub 引用列，但发布写入、持久化和投影没有保存它们。

## 变更

选本地文件后可直接上传至 OperitForge，或引用作者维护的 GitHub Release 资产。后一条路径由 Android 加载仓库 Release、选择资产并下载，确认与本地文件相同后登记；Worker 仍验证 canonical Release 与作者，再持久化 owner、repository 和 tag。

## 验收条件

- 两条路径使用同一资产契约。
- `market_assets` 保存 `gh_owner / gh_repo / gh_release_tag`。
- 条目和资产详情投影暴露该引用。
- 作者指南说明手工创建 Release 与应用侧登记。

## 原记录结果

代码和静态引用检查为 DONE；当时未要求测试，因此未执行测试。远端行为仍需独立验证。
