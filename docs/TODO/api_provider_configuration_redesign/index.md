# 模型与 API 供应商及协议重构

## 目标、范围与状态

状态：`AUTO-DETECTION REMOVAL, DRAWER UNIFICATION AND RESPONSES TOOL FIX IN PROGRESS / PRIOR DELIVERY COMPLETE`。

2026-08-21 用户再次调整当前方案：彻底删除“API 协议自动识别”，将当前仓库全部底部
`ModalBottomSheet` 统一为浏览器书签、历史和下载共用的三态抽屉，并修复 DeepSeek 及其他
Responses 供应商的工具调用结果关联。当前任务的唯一详细设计、12 处迁移矩阵和验收边界见
[`9_auto_detection_removal_drawer_unification_and_responses_tools.md`](9_auto_detection_removal_drawer_unification_and_responses_tools.md)。
本文件下方关于自动识别“保留并修复”的描述只属于上一轮历史实现，不再代表当前目标。

2026-08-21 用户复核后纠正了分组和协议范围：不设置“主流供应商”独立分类；OpenAI、
Anthropic 应位于国际供应商顶部，DeepSeek 应位于国内供应商顶部；供应商名称需要统一重命名，
并且协议选项必须逐供应商研究，不能只覆盖 OpenAI、Novita 和 Other。下方先前本地验证证据只
代表被纠正前的实现快照，不再构成当前最终交付证据。

本专项承接 2026-08-21 的中断任务，重构以下入口：

`软件首页 → AI助手 → 模型与API → API提供商`

目标是让用户先选择一个清晰的供应商，再在供应商配置内部选择实际协议、端点和协议相关
能力；同时修复当前 OpenAI Responses、Anthropic-compatible 端点和思考模式的运行时错配。

本轮 DeepSeek/多协议/长上下文后续方案不在本专项创建第二份协议架构；provider/protocol
能力边界、DeepSeek Responses replay-only 约束、canonical request 和 usage 语义统一见
[`../unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md`](../unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md)。

范围包括：

- 供应商身份、协议、端点默认值和协议选择器
- 供应商分组、排序、搜索、名称、摘要、颜色和按钮布局
- 现有模型配置 JSON、DataStore、连接测试、模型列表、AIServiceFactory 和请求编译链
- 普通思考模式与 `gpt-5.6*` 五档 reasoning effort
- 定向 JVM/Kotlin 验证、正式开发准备门禁、Debug APK、Git 审计、提交并推送 `main`

非目标：

- 不复制 sub2api/new-api 的计费、渠道权重、负载均衡、配额、运营后台或数据库
- 不操作设备、不安装 APK、不执行 Release/签名发布
- 不新增第二个模型配置状态 owner、第二份供应商存储或隐藏协议切换
- 不通过静默吞错、自动改写用户端点或未声明的协议切换掩盖配置错误

## 已验证基线

- Kiyori：实现前基线为 `main@d802052b6d247556f32509bd611a0d015808b590`，本地 `main`、
  `origin/main` 一致。
- `D:\10_Project\sub2api`：`main@3548256745c2168caf63e68963f2791834353eef`，干净。
- `D:\10_Project\new-api`：`main@f116414284162ad15d8925f7bca494c109b83e93`，干净。
- `python -B ci/script/check_formal_readiness.py --repository . --require-main` 已通过。
- Kiyori 尚未公开发行；本专项按未发布版本清理重复可见入口，但不删除仍被持久化、运行时、
  审计、备份或工具协议引用的旧标识。

## 设计决策

1. `ApiProviderType` 不再作为用户界面的协议目录。它仍保留为兼容枚举和供应商/运行时标识，
   新增序列化的 `ApiProtocol` 字段承担 `OPENAI_CHAT_COMPLETIONS`、`OPENAI_RESPONSES`、
   `ANTHROPIC_MESSAGES` 和 `PROVIDER_NATIVE` 四种协议语义。
2. 旧 JSON 没有 `apiProtocol` 时，根据 `apiProviderType` 和明确的旧端点规则推导协议；
   旧的 `OPENAI_RESPONSES`、`OPENAI_RESPONSES_GENERIC`、`ANTHROPIC_GENERIC` 等值继续可读。
   新 UI 不再显示这些重复入口，保存新选择时写入供应商身份加协议。
3. 所有可见供应商都由 `ApiProviderConfigs` 显式登记默认协议和完整协议列表；多协议供应商
   显示协议选择器，单协议供应商不显示无意义的单选控件。DeepSeek、阿里云百炼、OpenRouter、
   火山方舟、MiMo、LM Studio、Ollama 等已按各自能力登记，不能只覆盖 OpenAI、Novita 和
   Other。Anthropic-compatible 端点必须进入 `ClaudeProvider`，不能继续由
   `OpenAIProvider` 处理。
4. 供应商列表只保留国际供应商、国内供应商、本地与自定义、ToolPkg。OpenAI、Anthropic
   位于国际供应商顶部，DeepSeek 位于国内供应商顶部；不存在“主流供应商”独立分类。
5. 用户已有五档思考滑杆不变。普通 OpenAI Chat / Responses profile 使用
   `low/low/medium/high/high` 的显式 wire 映射；`gpt-5.6*` 使用
   `low/medium/high/xhigh/max` 五档映射。关闭思考时发送 `none` 的协议只对已声明
   reasoning 能力的 OpenAI 请求生效。
6. 不根据任意模型名称猜测 Codex 专属的 `xhigh/max`。只有 `gpt-5.6` 前缀进入 Codex
   五档 profile；普通 OpenAI profile 只声明标准三档。
7. endpoint 补全必须以 `ApiProtocol` 为主入口，旧的 `completeEndpoint(endpoint, providerType)`
   继续保留并委托到旧 ID 的协议推导，以保护既有测试和调用者。
8. 协议始终由用户显式选择并保存为具体 `ApiProtocol`；切换供应商时只应用 catalog 的明确
   默认协议。不存在自动识别入口、自动协议状态或运行时协议切换。

## 阶段状态

1. [DONE] 研究 Kiyori 当前供应商枚举、持久化、UI、请求工厂、端点补全和思考编译链。
2. [DONE] 对照 sub2api/new-api 的供应商/平台、协议/relay format 和表单组织方式。
3. [DONE] 冻结目标数据合同、UI 信息架构、思考模式和兼容边界。
4. [DONE] 重做无“主流供应商”的展示策略、统一命名和逐供应商协议矩阵。
5. [DONE] 按新矩阵修订协议选择器、自动识别、默认端点、连接测试、模型列表和运行时路由。
6. [DONE] 资源、文档、定向/完整测试、formal readiness、Debug APK 和产物审计已重新通过。
7. [DONE] 精确 Git 审计、提交、推送和远端 ref 对账。
8. [DONE LOCAL / PENDING DEVICE] 本轮删除自动识别、统一底部抽屉并修复 Responses 工具调用
   的本地实现与自动化合同已完成；目标设备上的供应商弹层、协议切换、保存、连接测试、Back、键盘、
   滚动和真实服务请求验收。

各阶段的文件范围和验收命令见下列分项文档。

本轮后续需求、根因、方案取舍、里程碑和验收矩阵见
[`8_followup_interaction_and_reasoning_design.md`](8_followup_interaction_and_reasoning_design.md)。

本轮新决策与当前实施状态见
[`9_auto_detection_removal_drawer_unification_and_responses_tools.md`](9_auto_detection_removal_drawer_unification_and_responses_tools.md)。

上一轮后续范围如下；其中自动识别已被本轮新决策删除：

- 上一轮曾修复配置阶段自动识别；当前实现和资源将在 M11 中彻底删除；
- 国际供应商新增 xAI（Grok 系列），国内分组移动到国际分组前；
- 删除海外供应商切换时的底部通知，保留并优化 API 协议下方说明；
- 供应商与协议选择器改为稳定底部抽屉；
- 修复上游模型选择抽屉的测量/滚动抖动；
- 五档思考程度改为低/中/高/极高/最高，并完善帮助文案；
- 为官方与兼容 endpoint 下的 gpt-5.6 Responses 补齐可见的服务端推理摘要请求、事件投影
  和去重；encrypted replay、background 与 sequence resume 仍限精确官方合同。

后续阶段编号从 M5 开始，M1-M4 的上一轮实现和交付证据仍保留在本专项历史中。

当前后续实现已通过最新 Kotlin 编译、`60` 项定向 JVM 测试、完整 JVM、资源/文档/架构门禁
和 Debug APK 审计；精确 Git 提交推送和远端对账仍在执行中，不能复用下方上一轮交付数字
作为本轮完成证据。

## 本轮后续本地验证证据

- 定向 JVM：`60` tests，零失败。
- 完整 JVM：`261` 份 JUnit XML、`1551` tests、`0` failures、`0` errors、`0` skipped。
- 七份目标语言 XML 可解析，`12` 个新增键各出现一次且占位符一致；architecture boundaries
  `m03`、formal readiness、fresh clone 和 `git diff --check` 均通过。
- Debug 构建：`.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`，
  `BUILD SUCCESSFUL in 1m 15s`；`232` 个任务中 `23` 个 executed、`209` 个 up-to-date。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，
  `2026-08-21T20:56:19.5383320+08:00`，`472745050` bytes，SHA-256
  `ECE56F04F306553E0EB42551FBF37C54068AB2E6898D4A68D1F938114097897E`。
- APK 身份：`com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，debuggable，
  唯一 launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`。
- 签名与对齐：Android Debug V2 单 signer；`zipalign -c -P 16 -v 4` 为
  `Verification successful`。
- 内容与 native：arm64-only；`51` 个 `.so` basename 零重复，`44` 个 DEX、`43` 个生产
  ToolPkg 文件、一份共享 AAPT2；包含 `liboperit_ripgrep.so` 与
  `assets/operit_shell_exec`，不包含 `libsudo.so`、本专项文档或测试包。
- `51` 个 `.so` 加 shell 共 `52` 个 ELF 均为 ELF64 AArch64；`153` 个 `PT_LOAD` 为
  `0x4000 × 151` 与 `0x10000 × 2`。

## 当前实现的本地验证证据

- `.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL`。
- 覆盖 catalog 完整性、逐供应商协议矩阵、自动识别、展示排序、endpoint completion、
  service route、模型列表 route、Chat/Responses reasoning、Anthropic endpoint/auth 和
  readiness 的定向 `testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 3m 9s`；当前 JUnit XML 为 `258` 份、`1534` tests、`0` failures、
  `0` errors、`0` skipped。
- `git diff --check` 通过；仅输出工作树换行规范提示，没有空白错误。
- `.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py --repository . --require-main`
  与 `.\.venv\Scripts\python.exe -B ci\script\check_fresh_clone.py --repository .` 均通过；
  fresh-clone 基线为 `d802052b6d247556f32509bd611a0d015808b590`。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 最终收尾构建：
  `BUILD SUCCESSFUL`，`232` 个任务中 `19` 个 executed、`213` 个 up-to-date；
  唯一 launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`，player runtime packaging
  门禁通过。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，mtime
  `2026-08-21T17:11:48.3777744+08:00`，`472731986` bytes，SHA-256
  `D2561E7F0550F59E5CC22AC1C5AEEAB73F1ADBDE559FAD5737C17020466A1FC0`。
- APK 身份为 `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，debuggable；
  Android Debug V2 单 signer，`zipalign -c -P 16 -v 4` 为 `Verification successful`。
- APK 仅含 `arm64-v8a`，`51` 个 `.so` basename 零重复，`44` 个 DEX、`43` 个生产
  ToolPkg 条目和一份共享模板 AAPT2；包含 `liboperit_ripgrep.so` 与
  `assets/operit_shell_exec`，不包含 `libsudo.so` 或本专项文档。DEX 未定义 JUnit、
  AndroidX Test、`com.kiyori.test` 或本专项测试类。
- `51` 个 `.so` 加 shell identity 共 `52` 个 ELF 均为 ELF64 AArch64；`153` 个
  `PT_LOAD` 为 `0x4000 × 151` 与 `0x10000 × 2`，没有低于 16 KB 的段。
- 最终 Git allowlist、敏感内容、构建产物、异常大文件、reparse/symlink、mode、子模块、
  嵌套 `.git` 和远端竞争审计通过；任务提交已推送 `origin/main`，本地 `HEAD`、
  `origin/main` 与远端 `refs/heads/main` 已完成一致性对账。

## 被需求纠正取代的本地验证快照

- 以下证据验证的是“存在主流分类、仅少数供应商可选协议”的旧候选实现，不能复用于最终交付。
- 七份语言资源 XML 均可解析，本专项 provider/protocol 键集合一致；旧的重复 OpenAI /
  Anthropic / Gemini 可见资源键和 protocol-specific section 键为 0。OpenAI 的供应商名称
  已统一为品牌/模型系列语义，Chat Completions 与 Responses 只在内部协议选择器中区分。
- `.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`：
  `BUILD SUCCESSFUL`，`83` 个任务中 `9` 个 executed、`74` 个 up-to-date。
- 覆盖协议迁移、路由、endpoint、模型列表、reasoning 请求体、Anthropic 官方地址和供应商
  展示策略的定向 `testDebugUnitTest`：`BUILD SUCCESSFUL`，`159` 个任务中 `9` 个 executed。
- 最终资源修正后的完整 `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`：
  `BUILD SUCCESSFUL`；JUnit XML 为 `257` 份、`1514` tests、`0` failures、`0` errors、
  `0` skipped。
- `check_formal_readiness.py --require-main` 与 `check_fresh_clone.py` 均通过；fresh-clone
  基线为 `d802052b6d247556f32509bd611a0d015808b590`。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL`，`232` 个任务中 `22` 个 executed、`210` 个 up-to-date；唯一 launcher
  和 player runtime packaging 门禁通过。
- Debug APK：
  `app/build/outputs/apk/debug/app-debug.apk`，`472730146` bytes，
  SHA-256 `5EA95FF7FD9D1EDEDADE6A7832F0DA691ADDF06242847851F0089A6A7ED1E071`；
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，debuggable。
- APK 使用 Android Debug V2 单 signer；`zipalign -c -P 16 -v 4` 为
  `Verification successful`。APK 仅含 `arm64-v8a`，`51` 个 `.so` basename 零重复，
  `44` 个 DEX、`43` 个生产 ToolPkg 条目、一份共享模板 AAPT2，并包含
  `liboperit_ripgrep.so` 与 `assets/operit_shell_exec`，不包含 `libsudo.so`、测试类或本专项
  文档。
- APK 内 `51` 个 `.so` 加 shell identity 共 `52` 个 ELF 均为 ELF64 AArch64；
  `153` 个 `PT_LOAD` 为 `0x4000 × 151` 与 `0x10000 × 2`，没有低于 16 KB 的段。
- 未安装或操作设备；供应商弹层视觉、IME/滚动/Back、真实 endpoint、连接测试和实际服务请求
  仍为 `verification_pending`。
