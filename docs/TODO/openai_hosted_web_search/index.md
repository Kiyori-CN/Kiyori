---
status: verification_pending
implementation: revision_7_local_complete_remote_revalidation_pending
baseline_branch: main
baseline_head: 180db686aaeedfa301b3c8a31057ee4940d58649
last_updated: 2026-08-13
---

# OpenAI 官方联网搜索插件化接入

## 1. 当前结论

OpenAI Hosted Web Search 作为独立工具提供给 DeepSeek、Gemini、Claude、本地模型和其他主聊天模型
在 Kiyori 中已经完成 revision `7` 的本地实现。revision `6` 的现场失败矩阵已转化为本地 URL
identity、域名策略、零证据、参数、日志隐私、证据投影和可观察性合同；真实 relay、设备和用户现场
复测仍未执行。

```text
任意主聊天模型
	└─ openai_web_search:search
		└─ com.kiyori.openai_web_search ToolPkg
			├─ settings UI
			├─ openai_web_search subpackage
			└─ Kiyori host
				├─ ToolPkgHostEnvironmentRepository
				├─ OpenAIHostedWebSearchBindingCompiler
				├─ OpenAIHostedWebSearchRequestLifecycle
				├─ OpenAIHostedWebSearchAdmissionController
				└─ OpenAIHostedWebSearchGateway
					└─ OpenAI Responses hosted web_search
```

GPT-5.6 搜索服务只承担查询规划、联网检索、网页阅读、证据整理和来源输出。主聊天模型继续负责用户
对话与最终回答；从 OpenAI 协议和计费角度看，搜索仍是一次独立模型请求。

当前合同：

```text
ToolPkg ID = com.kiyori.openai_web_search
ToolPkg version = 1.0.6
tool = openai_web_search:search
configuration source = PACKAGE_ENV only
host-service environment variables = 20
response schema revision = 7
container enabled by default = false
subpackage enabled after container activation = true
```

revision `7` 已完成源码、测试、ToolPkg `dist/`、权威文档、正式开发门禁和 Debug APK 本地验证。

### Revision 7 hotfix：完整 evidence 与主模型 projection 分流

revision `7` 的结果载荷现在有两个明确消费者：

- 聊天 UI 和结果卡读取 `formatToolResultForMessage()` 生成的完整 XML-safe JSON。该路径保留
  `all_sources`、`search_actions`、`query`、`usage`、citations 以及结果卡所需的完整 schema
- 后续主聊天模型读取 `formatToolResultForModel()` 生成的受限 projection。该路径只发送
  answer、source markers、cited sources、source summary、warnings 和必要 diagnostics，不把
  完整未引用来源集合再次扩展进主模型上下文

两条路径不能互换。UI parser 只解析完整 evidence；main-model projection 只服务后续模型请求。
这样可以同时保持结果卡所需的 `all_sources` 和主模型上下文边界，不再出现宿主先删除
`all_sources`、随后又由 UI parser 报告该字段缺失的错位。

compatibility record-set 的成功、失败和清理写入已改为同步提交，并在提交失败时显式暴露错误。
严格 relay 门禁仍然存在；同步提交只解决相同 fingerprint 的成功证据在进程立即退出时可能尚未
落盘的问题。fingerprint 输入或 response schema revision 变化后重新探测仍是预期行为。

2026-08-13 用户随后完成 `29` 次真实 relay 顶层调用。日志中的四批
`5 + 8 + 8 + 8` 与现场测试完全对齐：`23` 次结构化成功、`5` 次预期参数拒绝和 `1` 次非预期
零证据失败。该矩阵证明 revision `6` 的基础搜索质量可用，也暴露了必须进入 revision `7` 的
生产问题：

- relay 违反 `blocked_domains` 时，客户端没有 fail-closed 域名策略审计
- URL raw path 被多参数 `URI` 重建后产生 `%` 双重编码
- tracking-only URL 差异稳定触发 `CITATION_NOT_IN_ACTION_SOURCES`
- 零证据结果被提升为 `RELAY_RESPONSE_TEXT_ONLY`
- 空 Markdown 引用进入用户可见 answer
- 公开参数的 array/boolean 被错误建模为 string
- 成功结果缺少 timing、位置和 effective domain diagnostics
- 日志仍有 Key 前缀、完整 endpoint、正文 preview 和三重 ToolPkg 错误

详细证据、问题分级和 revision `7` 实施蓝图见
[`10_20260813_field_matrix_deep_analysis_and_revision_7_plan.md`](10_20260813_field_matrix_deep_analysis_and_revision_7_plan.md)。

## 2. Revision 6 决策

Kiyori 没有用户发布版本。旧 `MODEL_CONFIG` 要求用户输入正常 UI 无法获取的内部
`modelConfigId`，并带来第二配置源、Key selector、双重 limiter、悬空绑定和 readiness 失真。
revision `6` 已完整删除该专项链，不保留迁移窗口、兼容分支或回退路径。

本插件只使用：

```text
ToolPkgHostEnvironmentRepository
	→ compilePackageEnvironment()
	→ OpenAIHostedWebSearchPolicy
	→ OpenAIHostedWebSearchGateway
```

这项删除不影响全局四种 OpenAI 聊天 Provider：

```text
OPENAI
OPENAI_GENERIC
OPENAI_RESPONSES
OPENAI_RESPONSES_GENERIC
```

它们继续分别表示官方或兼容端点的 Chat Completions 与 Responses 协议。revision `7` 保持这条配置
边界，同时原子替换 revision `6` 响应合同，不保留旧 schema 字段的并行别名。

## 3. 当前实现

### 生命周期与错误分类

- `OpenAIHostedWebSearchRequestLifecycle` 是单请求唯一生命周期 owner
- 取消与 worker 完成通过同一个 `settle()` 领取唯一终态
- 第一次取消才执行 transport/job cancel
- callback 只投递一次
- OkHttp call timeout 在 cancellation 状态之前分类为 `REQUEST_TIMEOUT`
- 错误诊断包含 phase、cancel owner、submission state、elapsed、configured timeout、
  queue wait 和 provider request ID

### Admission 与预算

- 普通 search 与 compatibility probe 共用稳定的进程级 FIFO admission controller
- 支持并发、RPM、queue timeout、waiter cancel、配置热更新和快照
- queue timeout 默认 `60s`，不创建 HTTP Call
- HTTP call/read timeout 默认 `300s`
- connect timeout 上限 `30s`
- write timeout 上限 `60s`

### 配置与 readiness

- 二十个 package-scoped host-service 环境变量
- API Key 是 sensitive/password，ToolPkg JavaScript 不可读取
- extra headers 拒绝认证、`User-Agent` 和 hop-by-hop headers
- readiness 固定包含 provider contract、endpoint、model、credential、auth、extra headers、
  search options、admission、compatibility
- 设置 UI 通过无参数 `ToolPkg.services.openAIWebSearch.openConfiguration()` 打开固定原生配置页
- JavaScript 不能提交任意 package ID 或变量名

### 结果与 UI

- response schema revision 是 `7`，合法零证据使用 `evidence_mode=none`
- 成功结果分离 `cited_sources`、`all_sources`、`source_summary` 和 `execution_diagnostics`
- 主模型 projection 只发送 answer、source markers、cited sources、source counts、warnings 和必要
  diagnostics；UI 与原始 ToolResult 仍保留完整 `all_sources`
- 调用参数拒绝统一使用 `INVALID_ARGUMENT`，并携带 `field`、`reason` 和
  `submission_state=not_sent`
- ToolPkg 注册日志记录实际 version、artifact SHA-256、source kind、registration thread 和
  elapsed time；OpenAI Web Search 额外记录 response schema revision
- 递归工具链计时使用 `enhanced.toolSubtree.complete`、`round`、`depth`、`invocationId` 和
  `resultCount`
- evidence parser 返回 `NotApplicable`、`Parsed` 或 `Invalid`
- parser-invalid 日志有界且不记录 query、answer、sources 或完整 result
- 单个 `openai_web_search:search` 在 `READ_ONLY`、`ALL` 和 `FULL` 模式均建立 L0 工具组
- 结果卡分为搜索摘要、答案、来源、搜索轨迹、诊断与用量
- 来源保持顺序并按每批八条展开
- failure 与 parser-invalid 后仍保留普通 `ToolResultDisplay`
- warning 按 `INFO`、`WARNING` 和 `ERROR` 分级

## 4. 不变量

- 不自动读取当前聊天 Provider、配置、模型或自定义参数
- 不把搜索注册为完整聊天 Provider
- 不把 API Key 交给 ToolPkg JavaScript
- 不允许 request 参数覆盖 endpoint、model、Key、reasoning 或 headers
- 每次工具调用只提交一个非流式 Responses POST
- `store=false`
- `tool_choice=required`
- 只序列化实际非空的 allowed 或 blocked domain 数组
- 搜索失败不自动重试
- 搜索失败不切换 endpoint、模型、Key、Provider 或搜索后端
- 当前里程碑不使用 SSE、background、轮询、sequence resume 或断线续流
- 网页内容始终是不可信证据，不能改变 Kiyori 指令、权限或工具边界

## 5. 当前里程碑

```text
M1 lifecycle and error classification = COMPLETE
M2 admission and budgets = COMPLETE
M3 PACKAGE_ENV only = COMPLETE
M4 Provider, result card and parser UI = COMPLETE
M5 docs, full validation and Debug APK = HISTORICAL REVISION 6
R7-M0 URL/domain fixtures and identity = COMPLETE
R7-M1 domain policy and request boundary = COMPLETE
R7-M2 zero evidence and answer normalization = COMPLETE
R7-M3 ToolPkg typed arguments and error contract = COMPLETE
R7-M4 evidence projection and execution diagnostics = COMPLETE
R7-M5 log privacy and terminal error ownership = COMPLETE
R7-M6 ToolPkg/version/startup observability = LOCAL COMPLETE
R7-M7 documentation, validation and Debug APK = LOCAL COMPLETE
```

revision `7` 当前相关 JVM 矩阵：

```text
26 suites
142 tests
failures = 0
errors = 0
skipped = 0
```

另有 Kotlin production/unit-test 编译、TypeScript strict、dist 逐文件 SHA-256 稳定性和 formal
readiness 通过。当前 Debug APK 为 `472500125` bytes，最后写入时间为
`2026-08-14 00:11:46 +08:00`，SHA-256
`67BB9EFF412906712186B90C5FE78726F03563AC1AF235001C5F25A549F03204`；当前生成 ToolPkg 为
`10074` bytes，SHA-256
`9151D1614E5F1BF88FAA8A0A9306150FDB2C5F03EA8A957D2E767C1EC0120134`，两者与 APK 内条目
逐字节一致。

R7 本地验证摘要：

```text
TypeScript 5.9.3 deterministic generation = PASS
TypeScript strict --noEmit = PASS
Hosted Web Search JVM = 26 suites / 142 tests / 0 failures
Kotlin compile = PASS
formal readiness = PASS
Markdown/Git/secret/submodule audit = PASS
hotfix serial assembleDebug = PASS, 238 tasks / 28 executed / 210 up-to-date
APK signing/zipalign/ABI/native/credential audit = PASS
APK and ToolPkg hash reconciliation = PASS
```

revision `6` 历史 APK 为 `472492653` bytes，SHA-256
`BAEC6E5397101E89FD49439823CFCA8D720E850DA0AC3397E35D982FB9F0FFA3`。ToolPkg 为
`10243` bytes，SHA-256
`A7AFC0C19DD705E28EE29CE763113F7EA1F1C663F3471499A67964FC3F6C2FB7`，与 APK 内唯一条目
逐字节一致。APK 为 `com.kiyori / 45 / 0.1.0`、arm64-only、Debug V2 单 signer，
16 KB ZIP 对齐通过；`51` 个 `.so` 加 `operit_shell_exec` 共 `52/52` 个 `ELF64 AArch64`，
所有 `PT_LOAD >= 0x4000`。严格凭据扫描为零；完整证据见
[实施、测试与验收](4_implementation_and_validation.md)。

revision `7` 当前 Debug APK 已完成独立签名、zipalign、ABI、ELF 和敏感内容审计；真实 relay、
设备和用户验收仍不在当前授权范围内。

用户提供的最新日志 `kiyori_log_20260813_231439.txt` 在旧现场结果中多次记录：

```text
openai_web_search:search -> success
evidence_parse_invalid code=REQUIRED_FIELD_INVALID field=all_sources
```

这份日志是本轮根因复核证据，不是 revision `7` hotfix 的远端复测证据。当前仍需在明确授权
下完成真实 relay、安装后设备和用户现场验收。

## 6. 文档导航

- [官方合同与可行性](1_official_contract_and_feasibility.md)
- [插件与宿主架构](2_plugin_and_host_architecture.md)
- [请求、响应、凭据与安全](3_request_response_credentials_and_security.md)
- [实施、测试与验收](4_implementation_and_validation.md)
- [中转站与环境变量配置](5_relay_and_environment_configuration.md)
- [2026-08-12 日志深度分析](6_log_deep_analysis_20260812.md)
- [2026-08-12 后续修复计划](7_log_followup_fix_plan_20260812.md)
- [2026-08-13 优化调查检查点](8_20260813_readonly_analysis_checkpoint.md)
- [2026-08-13 优化实施计划](9_20260813_optimization_implementation_plan.md)
- [2026-08-13 现场矩阵深度分析与 revision 7 实施计划](10_20260813_field_matrix_deep_analysis_and_revision_7_plan.md)
- [正式架构](../../doc-src/architecture/openai_hosted_web_search.md)

## 7. 历史 revision 3–5 证据

以下内容是 revision `6` 设计来源，不是当前制品或当前 relay 证明。

### Revision 3

- Pixel Android 现场返回 HTTP `502`
- Sub2api Android 现场返回 HTTP `401`
- 脱敏宿主外矩阵证明只配置 `allowed_domains` 时发送空 `blocked_domains: []` 是共同请求根因
- ToolPkg 为 `9679` bytes，SHA-256
  `58025CC245D055D76345F45C9DE67E20D5464240F5033695B4C77380C1B94E24`
- Debug APK SHA-256
  `5767522CDEC73096B42A63E6CEA415A704E7F9AE56EA026F8FC17D468B1DF27C`

### Revision 4

- 修复空过滤数组后，Sekirocloud 现场出现非确定性 `SOURCE_INVALID`
- 原因是旧 parser 把 `action.sources` 错当作 citation 白名单
- `url_citation` 与 `action.sources` 可以独立、缺失或部分覆盖
- ToolPkg `1.0.3` 为 `9864` bytes，SHA-256
  `B1A5AFFF5D16FD742A941845FB7EE7EC19B59142F98D5681285F440BE84B480B`

### Revision 5

- 修复 citation 与 action-source 双通道 evidence 合同
- 完整定向矩阵为 `13` suites、`91/91` tests，失败、错误和跳过均为 `0`
- ToolPkg `1.0.4` 为 `9864` bytes，SHA-256
  `558382BDDE9688F99395F703D3225C7DAB5452326F85A6660DDB557ACEA3B9FB`
- Debug APK 为 `472480557` bytes，SHA-256
  `9F845CC0F71CEBB1929E42148F93C85A489C9FAD20160FAE5E8BFF13EE451536`
- 历史 APK 为 `com.kiyori / 0.1.0 (45)`，min/target/compile SDK `26 / 34 / 37`，
  唯一 launcher、arm64-only、Debug V2 单 signer、16 KB ZIP 对齐通过
- 51 个 `.so` 与 `assets/operit_shell_exec` 共 52 个 AArch64 ELF，历史审计
  `PT_LOAD >= 0x4000`

revision `3` 至 `6` 的旧 ToolPkg 和 APK 不得作为 revision `7` 当前交付物。

## 8. 2026-08-12 日志专项历史记录

指定日志证明 OpenAI Responses 请求在 Android 客户端读取 response headers 前连接中止，提交状态
保持未知。该证据不能确定 relay 服务端具体原因。

日志专项 W1–W6 的本地实现与历史封板已经完成：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_PENDING
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
```

该专项建立的 at-most-once、脱敏诊断、取消传播、绑定状态和 ANR/TextSegmenter 观察合同继续有效。
它不授权 revision `7` 当前任务调用真实 relay 或设备。

## 9. 当前授权与验收边界

revision `7` 当前现场与验收边界：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_REVALIDATION_PENDING
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
```

本轮 Codex 不运行真实 OpenAI/relay、设备、模拟器、ADB 或 APK 安装。用户已提供一轮真实 relay
矩阵，但 revision `7` 实现后仍需在独立授权下重新验证：

- 官方合同下的 domain policy
- relay filters 的本地 fail-closed
- URL fidelity 和 canonical source identity
- `evidence_mode=none`
- 空引用清理
- 320dp 窄屏
- 系统字体放大
- 中文/英文设置页视觉
- TalkBack 朗读
- 真实聊天消息的展开/收起手感
- 目标 Android 设备与用户验收

任何一级本地证据不能替代远端、设备或用户验收。
