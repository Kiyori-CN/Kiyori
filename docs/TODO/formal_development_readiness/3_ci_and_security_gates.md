# CI 与安全门禁

## 必须通过的自动检查

现有 `pr-check.yml` 保留候选提交、仓库卫生、lint baseline、Markdown、翻译和 YAML 检查，并新增：

- `check_formal_readiness.py`：身份、子模块、品牌 allowlist 和敏感/运行产物检查
- `check_fresh_clone.py`：从当前候选提交重新克隆并初始化 `terminal`，验证 gitlink 可获取
- `ci/test` 中的 Python 门禁单元测试

`android-build.yml` 在构建前执行同一正式开发门禁。Debug 构建用于持续验证；release bundle、签名和商店上传仍需单独授权。

生产 ToolPkg 不再由 workflow 同步到 `app/src/main/assets/packages/`。Android 构建和完整 Android PR 检查统一由 Gradle 从生产白名单生成资产；测试专用 ToolPkg 仍可由测试同步流程准备，不能混入生产源码 assets。

## 当前远端状态

2026-07-23 核对 `Kiyori-CN/Kiyori` 时，三个 workflow 文件均为 active，但仓库级 GitHub Actions 权限为 `enabled=false`。`main` 的推送事件已经到达 GitHub，Actions run 与 commit check 仍为 0；这属于远端仓库设置问题，不是 workflow 的 branch 或 path 条件未命中。

重新启用 Actions 会改变远端仓库状态并消耗 CI 资源，必须由仓库所有者明确授权。启用后应先手动运行 Android Build，再推送一个只含允许范围的候选提交，分别确认 `workflow_dispatch` 与 `push/main` 路径。

## 凭据边界

- `local.properties`、GitHub OAuth client secret、签名材料、API Key、Cookie 和令牌不得进入 Git
- 当前工作流中的 `OPERIT_GITHUB_CLIENT_ID` 与 `OPERIT_GITHUB_CLIENT_SECRET` 是已有 CI secret 名称，不是用户可见品牌；在配置迁移前保留，发行前另行完成 secret rotation
- Debug CI 使用空 OAuth 配置，不以私密凭据是否可用作为源码构建的前置条件

## 运行产物边界

`build/`、`app/build/`、`.gradle/`、`node_modules/`、`.venv/`、`local.properties`、生成的 `.toolpkg`、APK/AAB 和工作区 checkpoint 不得被提交。已审阅并随应用分发的 bundled APK 资源属于运行时输入，不是构建输出。检查脚本针对 Git 索引，不会删除本地文件。

## 失败处理

门禁失败时修复真实原因并重新运行对应检查。不得通过扩大 allowlist、跳过构建、提交生成文件或强制推送掩盖失败。需要改变兼容边界时，先更新本 TODO 和 `CONTEXT.md`，再修改代码。
