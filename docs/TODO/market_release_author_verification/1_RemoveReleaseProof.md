# 移除 Release 正文证明

## 历史实现

Android 从 `/market/v2/publish/proof` 获取签名证明，以 HTML 注释写入 GitHub Release 正文，再提交资产引用；Worker 解析该注释并验证。

## 变更

Worker 查询所选 Release，要求 `release.author.id` 等于市场会话的 `github_id`，并从该 Release 解析 GitHub canonical browser download URL。

## 验收条件

- 作者创建的 Release 无须 Operit 正文标记即可接受。
- 其他 GitHub 账户创建的 Release 被拒绝。
- 本地直接发布不调用 `publishProof`，也不在上传后修改正文。
- Worker 测试覆盖接受与拒绝路径。

## 原记录结果

代码和静态引用检查为 DONE；当时未要求测试，因此未执行测试。该结果不表示远端发布验收通过。
