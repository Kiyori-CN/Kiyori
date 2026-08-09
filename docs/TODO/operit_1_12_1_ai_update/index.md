---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Operit v1.12.1 必要 AI 插件发布更新

本轮以 Kiyori `main@2426e1e2`、上一轮已审计的 Operit `93a28251` 和
`v1.12.1@4faa5cd2` 为基线，只处理 Kiyori 现有 AI 插件发布链中的真实增量。

## 已确认事实

- Kiyori 已在 `1.12.0+9` 专项中合入待发送队列、超大消息读取、选中角色卡发送、
  ChatMessage Hook、共享 Hook 截止时间、角色卡 SoftwareSettings、长粘贴附件化和记忆提取规则
- `v1.12.1` 相对上一轮审计终点 `93a28251` 只新增 `4faa5cd2`
- 该提交删除市场登记失败后的 GitHub Release 回滚，并同时修改 Operit 自身的
  `versionCode` 与 `versionName`
- 当前 Kiyori 会在市场登记失败时删除新建 Release 或刚上传的资产；如果同名旧资产已经在上传前
  被替换，这条删除路径会让用户发布制品直接丢失
- 2026-08-09 只读检查公开市场的 13 个静态分页，共 `1257` 项；清单生成时间为
  `2026-08-07T10:21:18.056Z`，其中 `19` 个最新 ToolPkg 仍声明
  `minAppVer=1.12.0+9`，没有声明 `1.12.1` 的最新条目

## 必要更新

1. [DONE] 获取并核验 `v1.12.1` 标签、提交图和精确补丁
2. [DONE] 确认角色卡发送及 `1.12.0+9` AI/ToolPkg 更新已在 Kiyori 落地
3. [DONE] 删除市场登记失败后的 Release/asset 破坏性清理，保持
   `RegistrationFailed` 结果和已上传资产
4. [DONE] 增加源码合同测试，锁定登记失败分支不得调用 Release 或 asset 删除接口
5. [DONE] 运行定向 Python 测试、正式开发准备门禁、差异检查和 Debug APK 构建核验

## 非目标

- 不复制 Operit 的 `versionCode=46` 或 `versionName=1.12.1`
- 不提升 `OPERIT_MARKET_COMPAT_VERSION`；本轮没有对 A2A、远程 MCP、用户资料等
  `v1.12.1` 非市场发布增量进行全量对齐，不能用发布号替代真实运行时兼容证据
- 不改动现有 WASM、用户资料、主题系统、A2A、远程 MCP、签名、国际化、README 图片或 CI 策略
- 不改变同名 GitHub Release 资产上传前的明确替换语义
- 不创建 Release、不部署、不安装 APK、不操作设备

## 验收标准

- 市场登记失败返回原有 `PublishAttemptResult.RegistrationFailed`
- 新建 Release 和刚上传资产在登记失败后均不被删除
- 同名旧资产只在新资产上传前按现有流程删除
- Kiyori 产品版本和 Operit 市场兼容版本保持不变
- 定向检查、正式开发准备门禁、`git diff --check` 和规定的 Debug 构建通过

## 当前本地证据

- 新增资产保留合同测试 `2/2` 通过
- Markdown 链接解析测试 `7/7` 通过
- 正式开发准备门禁与 `git diff --check` 通过
- 完整 `ci/test` 执行 `176/176`，已同步修复播放器长按倍速源码合同漂移：
  `test_player_assets.py` 现在要求生产 `PlayerSession.kt` 使用的
  `resolveLongPressPlayerSpeed(snapshot.speed)`
- `:app:testDebugUnitTest` 为 `BUILD SUCCESSFUL`，JUnit XML 汇总
  `904/904`，零失败、零错误、零跳过
- `:app:lintDebug` 为 `BUILD SUCCESSFUL`，没有新增问题；既有
  `lint-baseline.xml` 继续过滤 `1208` 个错误、`4210` 个警告和 `149` 个提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 9s`，`238` 个任务中 `25` 个执行、`213` 个已是最新状态；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-09 14:05:43 +08:00`，大小 `475308770` 字节，SHA-256
  `2BEEE13F9CB206F8AC2EE9D5AA0DC85C0C2BA7B39B391863F374B2B20E903B0F`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 /
  minSdk 26 / targetSdk 34`；Android Debug V2 单签名和
  `zipalign -c -P 16 -v 4` 通过
- 本轮未创建真实 GitHub Release 或故意触发线上市场登记失败；远端发布链验收保持
  `verification_pending`

## 2026-08-09 后续质量收口与交付

当前用户已授权修复现有问题，并把审计后的全部当前工作树提交、推送到
`main` / `origin/main`。本段授权不包含 Release、部署、设备安装或远端市场测试。

1. [DONE] 重新获取 `origin/main` 并确认本地、跟踪和远端 `main` 均为 `2426e1e2`
2. [DONE] 稳定复现完整 Python 门禁的唯一失败：播放器长按倍速源码合同仍要求旧的
   `resolveNextPlayerSpeed`
3. [DONE] 将源码合同更新为当前生产实现使用的
   `resolveLongPressPlayerSpeed`，不把生产策略改回旧实现
4. [DONE] 重新运行完整 Python、JVM、Lint、正式准备检查、差异检查和 Debug 构建
5. [DONE] 审计敏感内容、异常大文件、构建产物、Git mode、子模块和最终暂存树
6. [DONE] 提交并推送 `main`，独立核对本地 HEAD、跟踪 ref 和远端 ref
