# 实现、验证与交付

当前状态：按用户纠正后的分组、命名、逐供应商协议范围和“自动识别”配置动作完成本地实现；
当前完整 JVM 回归、formal readiness、fresh clone、`git diff --check`、Debug APK 和
APK/ELF 产物审计已通过。main 精确审计、提交推送和远端对账已完成。设备不在本轮操作范围
内，保持 `verification_pending`。

## 里程碑

### M1：数据和协议边界

影响文件：

- `data/model/ModelConfigData.kt`
- `data/preferences/ModelConfigManager.kt`
- `data/collects/ApiProviderConfigCollect.kt`
- `api/chat/llmprovider/EndpointCompleter.kt`
- `api/chat/llmprovider/AIServiceFactory.kt`
- `api/chat/llmprovider/ClaudeProvider.kt`

交付：

- `ApiProtocol` 序列化字段和旧 ID 推导
- 协议感知的默认端点、endpoint options 和补全
- OpenAI Responses、Anthropic Messages、Novita Anthropic 的正确 provider 路由

验证：

- 旧 provider ID 推导测试
- endpoint completion 测试
- `NOVITA + ANTHROPIC_MESSAGES` 路由静态/单元测试
- `ChatConfigReadinessTest` 和连接测试编译

### M2：供应商目录和设置 UI

影响文件：

- `ui/features/settings/sections/ModelApiProviderPresentation.kt`
- `ui/features/settings/sections/ModelApiSettingsSection.kt`
- 七份 `strings.xml`

交付：

- canonical provider rows
- 国际/国内/本地与自定义/ToolPkg 分组
- 协议选择器、协议标签、搜索和 endpoint options
- 隐藏重复兼容入口，保留旧配置可见的 canonical 映射
- 统一供应商规范名称和按 protocol catalog 生成的能力摘要

验证：

- `ModelApiProviderPresentationPolicyTest`
- UI 静态反向检查：`FEATURED` 与“主流供应商”资源为 0，重复 OpenAI/Anthropic provider row 为 0
- 资源 XML 解析和语言键集合一致

### M3：思考 profile

影响文件：

- `api/chat/llmprovider/ModelCapabilityProfile.kt`
- `api/chat/llmprovider/OpenAIProvider.kt`
- `api/chat/llmprovider/OpenAIResponsesProvider.kt`
- `api/chat/llmprovider/ModelRequestCompilerTest.kt`

交付：

- 普通 OpenAI 三档 wire profile
- `gpt-5.6*` Codex 五档 profile
- Chat `reasoning_effort` 与 Responses `reasoning.effort` 请求体覆盖

验证：

- `ModelRequestCompilerTest`
- Chat/Responses request body 定向测试
- 关闭思考、普通模型、gpt-5.6*、官方/兼容 endpoint 全部覆盖

### M4：回归、文档和交付（已完成）

交付：

- 更新本专项状态、根任务索引和必要语义文档
- `git diff --check`
- formal readiness
- 定向 JVM/Kotlin 测试
- 串行 Debug APK 构建与 APK 核验
- 精确 Git allowlist、敏感内容、构建产物、子模块和远端 ref 审计
- 提交并推送 `main`

## 必要命令

```powershell
python -B ci/script/check_formal_readiness.py --repository . --require-main
python -B ci/script/check_fresh_clone.py --repository .
.\gradlew.bat :app:testDebugUnitTest --tests "*ModelRequestCompilerTest" --tests "*ModelApiProviderPresentationPolicyTest" --tests "*EndpointCompleter*"
git diff --check
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

额外单元测试只在改动风险需要时执行；本轮跨越数据模型、provider factory、模型列表、请求
编译和设置 UI，因此会在定向测试后执行完整 `testDebugUnitTest`。Debug APK 是本轮每次代码
修改后的必需收尾验证。
设备、Release、签名、远端 Actions 和真实供应商请求不由本地构建替代。

## 当前验证结果

- 编译：`.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`，
  `BUILD SUCCESSFUL`。
- 定向 JVM：catalog 完整性、逐供应商协议矩阵、自动识别、供应商展示排序、
  endpoint completion、service route、模型列表 route、Chat/Responses reasoning、
  Anthropic endpoint/auth 和 readiness 全部通过。
- 完整 JVM：`.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`，
  `BUILD SUCCESSFUL in 3m 9s`；当前报告为 `258` 份 JUnit XML、`1534` tests、
  `0` failures、`0` errors、`0` skipped。
- 仓库门禁：`git diff --check`、formal readiness、fresh clone 均通过；fresh-clone
  基线为 `d802052b6d247556f32509bd611a0d015808b590`。
- 最终 Debug 构建：`.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`，
  `BUILD SUCCESSFUL`，`232` 个任务中 `19` 个 executed、`213` 个 up-to-date；
  唯一 launcher 和 player runtime packaging 门禁通过。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，
  `2026-08-21T17:11:48.3777744+08:00`，`472731986` bytes，SHA-256
  `D2561E7F0550F59E5CC22AC1C5AEEAB73F1ADBDE559FAD5737C17020466A1FC0`；
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，debuggable。
- 签名与对齐：Android Debug V2 单 signer；`zipalign -c -P 16 -v 4` 为
  `Verification successful`。
- APK 内容：arm64-only，`51` 个 `.so` basename 零重复，`44` 个 DEX、`43` 个生产
  ToolPkg 条目、一份共享模板 AAPT2、ripgrep 和 shell identity 均存在；`libsudo.so`、
  本专项文档、JUnit/AndroidX Test/`com.kiyori.test` 和本专项测试类均未打包。
- Native：`51` 个 `.so` 加 shell identity 共 `52` 个 ELF64 AArch64，`153` 个
  `PT_LOAD` 为 `0x4000 × 151` 与 `0x10000 × 2`，没有低于 16 KB 的段。
- Git 交付：精确 allowlist、敏感内容、构建产物、异常大文件、reparse/symlink、mode、
  子模块、嵌套 `.git` 和远端竞争审计通过；任务提交已推送 `origin/main`，本地 `HEAD`、
  `origin/main` 与远端 `refs/heads/main` 已完成一致性对账。

## 被需求纠正取代的旧候选结果

- 以下结果只证明旧候选可编译、可测试和可构建，不能证明新需求完成。
- 资源：七份目标语言 XML 解析通过，本专项 provider/protocol 键一致，旧重复可见键为 0。
- 编译：`:app:compileDebugKotlin` 通过。
- 定向测试：协议推导与持久化、供应商目录与协议选项、endpoint completion、service route、
  模型列表 route、Chat/Responses reasoning 请求体、Anthropic 官方 endpoint 和 readiness
  全部通过。
- 完整 JVM：`257` 份 JUnit XML、`1514` tests、零失败/错误/跳过。
- 准备门禁：formal readiness 与 fresh clone 均通过。
- Debug APK：`472730146` bytes，SHA-256
  `5EA95FF7FD9D1EDEDADE6A7832F0DA691ADDF06242847851F0089A6A7ED1E071`；
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，Android Debug V2 单 signer，
  16 KB ZIP alignment 通过。
- Native/runtime：arm64-only，`51` 个 `.so` basename 零重复；加 shell identity 共
  `52` 个 ELF64 AArch64，`153` 个 `PT_LOAD` 全部不低于 `0x4000`；唯一 launcher、
  player runtime、ripgrep、生产 ToolPkg 和共享 AAPT2 合同通过。
- 未执行设备、真实服务和用户验收，终态必须保留 `verification_pending`。

## 完成标准

- 供应商弹层不再显示重复 OpenAI/Anthropic protocol rows。
- 每个可选协议在 UI、端点补全、连接测试、provider factory 和请求体中保持一致。
- 旧配置可读取，ToolPkg、审计、Responses persistence 和模型绑定未被复制或破坏。
- 普通 OpenAI 模型可使用思考模式；`gpt-5.6*` 五档映射完整且有请求体证据。
- 定向验证、formal readiness、Debug APK 和 Git/远端对账全部通过。
- 未执行设备验收时，最终状态保持 `verification_pending`。
