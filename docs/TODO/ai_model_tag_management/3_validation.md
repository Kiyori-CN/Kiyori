# 3. 验证与交付

## 自动化检查

- `ModelNameListTest`：修正版 `9/9` 通过
- `ModelNameTagEditorContractTest`：第五轮 `4/4` 通过
- `ModelTagFlowPlanTest`：`6/6` 通过
- 第五轮定向合计：`19` 项测试，失败 `0`、错误 `0`、跳过 `0`
- 首轮额外覆盖 `KiyoriSettingsPagesTest` `12/12` 与 `ChatUtilsProviderModelTest` `4/4`
- `python -B ci/script/check_formal_readiness.py --repository . --require-main`：PASS
- `python -B -m unittest ci.test.test_markdown_links`：`7/7` 通过
- README、CONTEXT、总 TODO 与本任务 5 份 Markdown 的当前链接检查：8 份、0 个问题
- `git diff --check`：无 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：BUILD SUCCESSFUL
- 构建任务同时通过 `verifySingleDebugLauncher` 和 `verifyDebugPlayerRuntimePackaging`

第五轮新增验证覆盖默认三行、真实隐藏数量、无删除叉号展开标签，以及清空全部的 `Checking`、`Confirm`、`Blocked` 模态状态契约。

## Debug APK

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 构建主机文件时间：`2026-08-09 02:57:45 +08:00`
- 会话日期：`2026-08-08`；上述文件时间只作为产物元数据
- 大小：`471620006` bytes
- application ID：`com.kiyori`
- version：`0.1.0 (45)`
- compileSdk：`37`
- targetSdk：`34`
- native ABI：仅 `arm64-v8a`
- SHA-256：`4642C2CD74B5F7A82729B44D4F004D879B2737807D5D058BD9DC948E7FE5A857`
- `apksigner verify --verbose --print-certs`：Android Debug，V2 签名通过
- `zipalign -c -P 16 -v 4`：`Verification successful`

## 未覆盖范围

- Android 真机视觉和触摸验收
- 崩溃报告 `b79f91c3-11c9-4ea5-87d0-00e34a812f79` 对应路径的修正版真机复测
- 真实 API 上游模型列表返回质量
- 用户现场复制、删除、排序和连接测试体验

以上项目在本地实现和 Debug 构建通过后仍保持 `verification_pending`。
