# CI 与安全门禁

## 必须通过的自动检查

现有 `pr-check.yml` 保留候选提交、仓库卫生、lint baseline、Markdown、翻译和 YAML 检查，并新增：

- `check_formal_readiness.py`：身份、子模块、品牌 allowlist 和敏感/运行产物检查
- `check_fresh_clone.py`：从当前候选提交重新克隆并初始化 `terminal`，验证 gitlink 可获取
- `ci/test` 中的 Python 门禁单元测试

`android-build.yml` 在构建前执行同一正式开发门禁。Debug 构建用于持续验证；release bundle、签名和商店上传仍需单独授权。

## 凭据边界

- `local.properties`、GitHub OAuth client secret、签名材料、API Key、Cookie 和令牌不得进入 Git
- 当前工作流中的 `OPERIT_GITHUB_CLIENT_ID` 与 `OPERIT_GITHUB_CLIENT_SECRET` 是已有 CI secret 名称，不是用户可见品牌；在配置迁移前保留，发行前另行完成 secret rotation
- Debug CI 使用空 OAuth 配置，不以私密凭据是否可用作为源码构建的前置条件

## 运行产物边界

`build/`、`app/build/`、`.gradle/`、`node_modules/`、`.venv/`、`local.properties`、生成的 APK/AAB 和工作区 checkpoint 不得被提交。已审阅并随应用分发的 bundled APK 资源属于运行时输入，不是构建输出。检查脚本针对 Git 索引，不会删除本地文件。

## 失败处理

门禁失败时修复真实原因并重新运行对应检查。不得通过扩大 allowlist、跳过构建、提交生成文件或强制推送掩盖失败。需要改变兼容边界时，先更新本 TODO 和 `CONTEXT.md`，再修改代码。
