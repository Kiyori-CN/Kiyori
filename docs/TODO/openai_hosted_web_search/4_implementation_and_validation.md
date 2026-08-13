# 4. 实施、测试与验收

## 4.1 当前状态与授权边界

revision `7` 当前状态：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_REVALIDATION_PENDING
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
```

当前已授权提交和推送本轮有效修改；当前仍不允许：

- 创建分支、PR 或发布
- 调用真实 OpenAI、relay 或可能计费的接口
- 操作真机、模拟器、ADB 或 MuMu
- 安装 APK

revision `6` 的 29 次真实 relay 顶层调用只作为 revision `7` 的问题基线：
`23` 次结构化成功、`5` 次预期参数拒绝和 `1` 次非预期零证据失败。该现场矩阵不构成
revision `7` 的远端复测或用户验收证据。

## 4.2 Revision 7 本地实现与验证

revision `7` 已完成本地源码、测试、ToolPkg 生成制品和文档合同同步。当前合同为：

```text
ToolPkg version = 1.0.6
host-service environment variables = 20
response schema revision = 7
configuration source = PACKAGE_ENV only
```

本地实现包含：

- `cited_sources`、`all_sources`、`source_summary` 和 `execution_diagnostics` 的原子响应合同
- display URL identity 与 canonical URL identity 分离，保持 percent-encoding fidelity，并处理 tracking-only 差异
- 官方响应域名策略 fail-closed；relay 带 domain filters 时在 HTTP Call 创建前返回
  `DOMAIN_FILTER_UNSUPPORTED_FOR_RELAY` 与 `submission_state=not_sent`
- `evidence_mode=none` 与合法零证据成功 envelope，零证据使用 `NO_WEB_EVIDENCE` warning
- 空 Markdown 链接/图片清理及 citation offset 映射
- 单调时钟 lifecycle timing、成功/失败 execution diagnostics 和
  `enhanced.toolSubtree.complete` 递归工具树计时
- 主模型 cited-source projection 不重复发送完整 `all_sources`、search actions、query、usage
  或原始 citations
- ToolPkg 原生 array/boolean 参数、字段级 `INVALID_ARGUMENT` 与结构化拒绝
- ToolPkg 注册 version、artifact SHA-256、source type、registration thread 和 elapsed observation
- 设置保存、TTS/自动朗读、ToolPkg 参数转换和终态错误日志的隐私/所有权收口

本轮 revision `7` hotfix 进一步固定两个调用契约：

- `formatToolResultForMessage()` 只用于实时聊天结果和 UI evidence，保留完整
  `all_sources`；`formatToolResultForModel()` 只用于后续主模型请求，使用受限 projection。
  `buildBoundedToolResultMessage()` 只能走后者，实时工具结果收集器只能走前者
- `OpenAIHostedWebSearchCompatibilityRepository` 的成功、失败和清理写入使用同步
  `SharedPreferences.commit()`，并在返回 callback 前确认提交成功。该改动不放宽
  `requireRelayProbe=true`，也不改变 fingerprint 变化后的重新探测规则

revision `7` 本地验证证据：

```text
Hosted Web Search JVM = 26 suites / 142 tests
failures = 0
errors = 0
skipped = 0
Kotlin production/unit-test compile = PASS
TypeScript strict = PASS
dist SHA-256 stability = PASS
formal development readiness = PASS
```

上述结果只证明本地实现与构建输入一致。真实 relay revalidation、设备安装与复测、320dp/字体放大、
TalkBack、真实聊天视觉和用户 acceptance 仍待执行。

## 4.3 Revision 6 历史基线

### M1：生命周期与错误分类 `[LOCAL DONE]`

主要实现：

```text
OpenAIHostedWebSearchRequestLifecycle
OpenAIHostedWebSearchRequestPhase
OpenAIHostedWebSearchSubmissionState
OpenAIHostedWebSearchCancellationOwner
OpenAIHostedWebSearchTerminalOutcome
```

合同：

- 取消与 worker 完成共用一个 lifecycle owner
- 第一次取消进入 `CANCEL_REQUESTED`
- 只有第一次取消实际执行 transport/job cancel
- `settle()` 领取唯一终态
- 取消先发生时，后续成功结果收敛为 `CANCELLED`
- success 先 settle 后，新的取消返回 false
- callback 只投递一次
- OkHttp call timeout 在 cancellation 状态之前分类为 `REQUEST_TIMEOUT`

错误诊断字段：

```text
phase
cancel_owner
submission_state
elapsed_ms
configured_timeout_ms
queue_wait_ms
provider_request_id
```

### M2：Admission 与预算 `[LOCAL DONE]`

`OpenAIHostedWebSearchAdmissionController` 是普通 search 与 compatibility probe 共用的进程内稳定
FIFO owner。

覆盖：

- 最大并发
- RPM
- queue timeout
- waiter cancel 精确移除
- 配置热更新
- active、queued 与窗口 timestamp 快照

默认预算：

```text
queue timeout   = 60s
call timeout    = 300s
read timeout    = 300s
connect timeout = min(call timeout, 30s)
write timeout   = min(call timeout, 60s)
```

queue timeout 不创建 HTTP Call，提交状态保持 `not_sent`。

### M3：唯一 PACKAGE_ENV `[LOCAL DONE]`

revision `6` 完整删除本插件专项 `MODEL_CONFIG` 链，包括：

```text
OPENAI_WEB_SEARCH_CONFIG_SOURCE
OPENAI_WEB_SEARCH_MODEL_CONFIG_ID
OpenAIHostedWebSearchConfigSource
compileModelConfig()
OpenAIHostedWebSearchModelConfigKeySelector
modelConfigId
modelConfigMaxConcurrentRequests
modelConfigRequestsPerMinute
modelConfigKeySelectionMutex
MODEL_CONFIG_NOT_FOUND
MODEL_NOT_IN_CONFIG
advanceModelConfigKey
```

当前唯一配置链：

```text
ToolPkgHostEnvironmentRepository
	→ OpenAIHostedWebSearchBindingCompiler.compilePackageEnvironment()
	→ OpenAIHostedWebSearchPolicy
	→ OpenAIHostedWebSearchGateway
```

当前合同：

```text
ToolPkg version = 1.0.5
host-service environment variables = 20
response schema revision = 6
```

revision `5` record-set 被 revision `6` codec 拒绝。错误 JSON 中已删除 `retryable`。
`User-Agent` 已加入 extra-header 禁止覆盖名单。

readiness 固定包含九项：

```text
provider_contract
endpoint
model
credential
auth
extra_headers
search_options
admission
compatibility
```

固定原生配置入口：

```ts
ToolPkg.services.openAIWebSearch.openConfiguration()
```

宿主固定打开 `com.kiyori.openai_web_search` 的环境变量编辑器。

### M4：Provider、结果卡与 parser `[LOCAL DONE]`

Evidence parser 返回：

```text
NotApplicable
Parsed
Invalid
```

`Invalid` 只暴露脱敏诊断：

```text
code
sanitizedSummary
schemaRevision
hasRequestId
fieldName
```

有界 parser-invalid 日志最多持有 `128` 个键，不记录 query、answer、sources 或完整 result。

单个精确工具名 `openai_web_search:search` 在 `READ_ONLY`、`ALL` 和 `FULL` 三种模式都建立消息级
L0 工具组，继续复用既有 `rendererId`、`stableKey`、`expanded`、`userOverride` 和
`hasLiveXmlStream` owner。

结果卡层级：

```text
L0 消息级工具组
L1 搜索摘要
L2 答案
L3 来源
L3 搜索轨迹
L3 诊断与用量
```

L1 默认收起。来源、搜索轨迹和诊断独立折叠；来源保持原顺序并按每批八条展开。failure 与
parser-invalid 卡仍保留普通 `ToolResultDisplay`。

全局四种 OpenAI Provider 枚举保留：

```text
OPENAI
OPENAI_GENERIC
OPENAI_RESPONSES
OPENAI_RESPONSES_GENERIC
```

用户可见名称明确区分 Chat Completions / Responses 与官方 / 兼容端点。选择器按协议分组，并保留
其他内建 Provider 与动态 ToolPkg Provider。已选 Provider 使用多行全名和协议/端点摘要。

M4 最新定向测试 XML：

```text
OpenAIHostedWebSearchEvidenceParserTest              9
OpenAIWebSearchResultPresentationPolicyTest          7
ThinkToolsXmlNodeGrouperTest                         4
ModelApiProviderPresentationPolicyTest               4
```

合计 `24` tests，失败、错误和跳过均为 `0`。前台重跑为 `BUILD SUCCESSFUL in 37s`。

### M5：文档、完整验证与 Debug APK `[LOCAL DONE]`

M5 已完成的构建前门禁：

- [x] 中英文资源无硬编码遗漏
- [x] `CONTEXT.md` 与正式架构文档同步 revision `6`
- [x] 专项 2–5 号合同、index 与总 TODO 同步
- [x] `dist/` 由 TypeScript 编译器重生成
- [x] 两次生成 SHA-256 逐文件一致
- [x] TypeScript strict typecheck 通过
- [x] 完整 Hosted Web Search JVM 矩阵通过
- [x] Debug Kotlin 与 unit-test Kotlin 编译通过
- [x] formal readiness 通过
- [x] Markdown、Git、敏感信息、归档和子模块审计通过
- [x] 第一次串行 `:app:assembleDebug` 通过
- [x] 第一次 APK、ToolPkg、签名、ABI、16 KB、native 与敏感信息审计通过
- [x] 文档证据回填后的串行 `:app:assembleDebug` 通过并复核制品

文档证据回填后的稳定树构建为：

```text
BUILD SUCCESSFUL in 26s
238 actionable tasks: 25 executed, 213 up-to-date
verifySingleDebugLauncher = PASS
verifyDebugPlayerRuntimePackaging = PASS
```

APK 与 ToolPkg 的大小、时间和 SHA-256 均保持不变。

## 4.4 自动验证矩阵

### TypeScript 与 ToolPkg

```powershell
npx tsc -p examples\openai_web_search\tsconfig.json
npx tsc -p examples\openai_web_search\tsconfig.json --noEmit
```

两次生成后分别计算：

```text
dist/main.js
dist/shared.js
dist/packages/openai_web_search.js
dist/ui/index.ui.js
```

SHA-256 必须逐文件一致。

当前结果：

```text
TypeScript = 5.9.3
generation 1 = PASS
generation 2 = PASS
strict --noEmit = PASS

dist/main.js
	714 bytes
	B3120A78AA107D3FB87285F504A6A0B7AC91A085B0C0B9EADEADB05D66109146

dist/packages/openai_web_search.js
	3691 bytes
	DA783A3626BE11C10ADB666E8CF6A8216E775276279B732B8E2A41C2327DF6D6

dist/shared.js
	5527 bytes
	C8E67824A53BA6B85884A3617A012B5D356E533A74A8D2A398A456B07ECD685A

dist/ui/index.ui.js
	14588 bytes
	4171C6D652D5D8BF8D7696E7B7BAA0C377F80AA3017F01C9949F96333B5E3E38
```

两次生成的四个 hash 逐一相同。

归档只允许：

```text
manifest.json
dist/main.js
dist/shared.js
dist/packages/openai_web_search.js
dist/ui/index.ui.js
```

### Hosted Web Search JVM

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearch*' `
  --tests 'com.ai.assistance.operit.api.chat.llmprovider.ToolPkgOpenAIWebSearchBridgePolicyTest' `
  --tests 'com.ai.assistance.operit.api.chat.ToolSubtreeTraceTest' `
  --tests 'com.ai.assistance.operit.core.tools.javascript.ToolPkgInvocationArgumentErrorTest' `
  --tests 'com.ai.assistance.operit.core.tools.javascript.JsToolPkgTerminalErrorPolicyTest' `
  --tests 'com.ai.assistance.operit.ui.features.packages.screens.ToolPkgHostEnvironmentEditRequestStoreTest' `
  --tests 'com.ai.assistance.operit.core.tools.packTool.OpenAIWebSearchToolPkgContractTest' `
  --tests 'com.ai.assistance.operit.core.tools.packTool.ToolPkgRegistrationObservationPolicyTest' `
  --tests 'com.ai.assistance.operit.services.core.SpeechLogPrivacyTest' `
  --tests 'com.ai.assistance.operit.ui.features.chat.components.part.OpenAIWebSearchResultPresentationPolicyTest' `
  --tests 'com.ai.assistance.operit.ui.features.chat.components.part.ThinkToolsXmlNodeGrouperTest' `
  --tests 'com.ai.assistance.operit.ui.features.settings.sections.ModelApiProviderPresentationPolicyTest' `
  --no-daemon --console=plain
```

当前结果：

```text
BUILD SUCCESSFUL in 23s
159 actionable tasks: 2 executed, 157 up-to-date
26 suites
142 tests
failures = 0
errors = 0
skipped = 0
```

suite：

```text
OpenAIHostedWebSearchAdmissionControllerTest          5
OpenAIHostedWebSearchBindingCompilerTest              5
OpenAIHostedWebSearchCompatibilityProbePolicyTest     5
OpenAIHostedWebSearchCompatibilityRecordSetTest       7
OpenAIHostedWebSearchEvidenceParserTest               9
OpenAIHostedWebSearchGatewayTest                      8
OpenAIHostedWebSearchHttpFailurePolicyTest            3
OpenAIHostedWebSearchPolicyTest                       5
OpenAIHostedWebSearchReadinessTest                    3
OpenAIHostedWebSearchRequestCompilerTest              3
OpenAIHostedWebSearchResponseParserTest              17
ToolPkgOpenAIWebSearchBridgePolicyTest               15
OpenAIWebSearchToolPkgContractTest                    5
OpenAIWebSearchResultPresentationPolicyTest           7
ThinkToolsXmlNodeGrouperTest                          4
ToolPkgHostEnvironmentEditRequestStoreTest            1
ModelApiProviderPresentationPolicyTest                4
```

### Kotlin 编译

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin `
  --no-daemon --console=plain
```

当前结果：

```text
BUILD SUCCESSFUL in 17s
137 actionable tasks: 1 executed, 136 up-to-date
```

### 正式开发门禁

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py `
  --repository . `
  --require-main
```

当前结果：

```text
Formal development readiness: PASS
```

通过身份与版本、子模块、品牌、tracked secret/runtime hygiene、生成式 shell launcher 与 terminal
shim、SSH 密码传输和 CI compileSdk 对齐检查。

### 构建前仓库审计

当前结果：

- `main@180db686aaeedfa301b3c8a31057ee4940d58649`
- `HEAD...origin/main = 0/0`
- staged changes 为零
- `git diff --check` 无 whitespace error，仅有既有 CRLF 转换提示
- `terminal` 子模块干净并对齐 gitlink
- 历史 hotbuild gitlink 未初始化，本轮未修改
- 十个变更 Markdown 文件相对链接全部有效，未新增 Mermaid
- 高置信 OpenAI/GitHub/AWS token、Bearer credential 和私钥命中为零
- 变更文件中没有超过 5 MiB 的异常大文件
- 新增 URL 仅为官方 OpenAI endpoint 与 `.example` 测试域名
- 新增生产/测试差异没有 fallback、自动 retry、suppress、Lint baseline 扩张或新 TODO
- manifest 为 `1.0.6`、二十个唯一环境变量、一个 subpackage，旧配置字段为零

### Debug APK

revision `7` 当前树已串行执行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

结果：

```text
BUILD SUCCESSFUL in 1m 5s
238 actionable tasks: 28 executed, 210 up-to-date
verifySingleDebugLauncher = PASS
verifyDebugPlayerRuntimePackaging = PASS
```

路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 4.5 Revision 7 当前 APK 审计

当前 revision `7` Debug 制品已经独立核验：

- APK：`472500125` bytes，最后写入时间 `2026-08-14 00:11:46 +08:00`
- APK SHA-256：
  `67BB9EFF412906712186B90C5FE78726F03563AC1AF235001C5F25A549F03204`
- package/version：`com.kiyori / 45 / 0.1.0`
- min/target/compile SDK：`26 / 34 / 37`
- application label：`Kiyori`
- 唯一 launcher：`com.ai.assistance.operit.ui.main.MainActivity`
- ABI 仅 `arm64-v8a`
- Android Debug V2 单 signer，certificate SHA-256：
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`
- `zipalign -c -P 16 -v 4` 为 `Verification successful`
- APK 中 `51` 个 arm64 `.so` basename 唯一
- `51` 个 `.so` 与 `assets/operit_shell_exec` 共 `52/52` 个 `ELF64 AArch64`
- `PT_LOAD` 最小对齐为 `0x4000`，对齐分布为 `0x4000: 158`、`0x10000: 2`，低于
  `0x4000` 的 segment 数为 `0`
- APK 中只有一份 `assets/packages/openai_web_search.toolpkg`
- 生成 ToolPkg：`10074` bytes，时间 `2026-08-13 22:38:34 +08:00`
- ToolPkg SHA-256：
  `9151D1614E5F1BF88FAA8A0A9306150FDB2C5F03EA8A957D2E767C1EC0120134`
- 生成 ToolPkg 与 APK 内条目逐字节一致
- ToolPkg manifest 为 `com.kiyori.openai_web_search`、version `1.0.6`，包含 `20` 个环境变量和
  `1` 个 subpackage；归档只包含五个预期文件，无 `.env`、TypeScript 源码、`node_modules`、
  缓存或 source map
- 全 APK 的 OpenAI Key、Bearer credential、GitHub/AWS token 和完整私钥材料扫描均为 `0`
- 私钥头正则只命中既有解析器、加密库与 `libmpformat.so` 的 PEM 协议字面量；未发现配对的真实
  私钥正文。credential URL 形状只命中 `assets/subpack/android.apk` 内既有
  `localhost:<placeholder>@...` 文本，不是可用账号或远端凭据
- OpenAI Web Search ToolPkg 对上述全部凭据模式均为 `0`
- APK 唯一 `.ts` 是既有受跟踪模板
  `assets/templates/typescript/src/index.ts`，与本插件制品无关且和源码逐字节一致
- APK 非公网 URL 只来自既有 loopback 服务和 `workspace-preview.local` 应用内预览域
- 当前差异新增 URL 只使用 `api.openai.com` 和 `relay.example`，不携带凭据

生成制品：

```text
app/build/generated/bundledToolPkgAssets/packages/openai_web_search.toolpkg
```

APK 条目：

```text
assets/packages/openai_web_search.toolpkg
```

以上证据来自当前 revision `7` 树的串行构建和独立审计。真实 relay、设备 UI 和用户验收继续
保持待验证。

### Revision 6 历史 APK 审计

以下数字保留用于解释 revision `6` 的历史封板，不作为当前 revision `7` 制品：

- APK：`472492653` bytes，SHA-256
  `BAEC6E5397101E89FD49439823CFCA8D720E850DA0AC3397E35D982FB9F0FFA3`
- ToolPkg：`10243` bytes，SHA-256
  `A7AFC0C19DD705E28EE29CE763113F7EA1F1C663F3471499A67964FC3F6C2FB7`

## 4.6 历史 revision 3–5 证据

历史现场与本地数据保留用于解释 revision `6` 的设计来源，不作为当前交付物：

- revision `3` 暴露空 `blocked_domains` relay 兼容问题
- revision `4` 暴露 citation 与 `action.sources` 错误绑定造成的 `SOURCE_INVALID`
- revision `5` 修复双通道 evidence 合同并完成历史本地封板
- revision `5` ToolPkg `1.0.4` 为 `9864` bytes，SHA-256
  `558382BDDE9688F99395F703D3225C7DAB5452326F85A6660DDB557ACEA3B9FB`
- revision `5` APK 为 `472480557` bytes，SHA-256
  `9F845CC0F71CEBB1929E42148F93C85A489C9FAD20160FAE5E8BFF13EE451536`

完整历史请求、relay、parser 和制品证据见
[专项索引](index.md)、[日志深度分析](6_log_deep_analysis_20260812.md) 与
[后续修复计划](7_log_followup_fix_plan_20260812.md)。

## 4.7 待验证

当前不运行：

- 真实 relay compatibility probe
- 真实 `openai_web_search:search`
- 320dp 窄屏
- 系统字体放大
- 中文/英文设置页真机视觉
- TalkBack 实机朗读
- 真实聊天消息的展开/收起手感
- APK 安装与目标设备复测

最终本地收尾后仍保持：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_REVALIDATION_PENDING
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
```

## 4.8 2026-08-13 现场矩阵

最新日志：

```text
kiyori_log_20260813_193725.txt
170628 bytes
SHA-256 BB2F0124ADD4856CD84DEC34A6337EEA743FC48A8F2C4F23607F9D7CBA3396FF
```

日志包含四批 `5 + 8 + 8 + 8` 个 `openai_web_search:search`，合计 `29` 次顶层调用，与用户测试报告
完全对齐：

```text
23 structured success
5 expected invalid-argument rejection
1 unexpected no-evidence failure
```

revision `6` 本地自动测试和 APK 静态封板仍是有效的历史证据，但它们没有覆盖本次现场暴露的：

- blocked-domain relay 违规
- percent-encoded path 双重编码
- tracking query canonical identity
- 合法零证据
- 空 Markdown 引用
- array/boolean 公开参数类型
- 成功 timing、位置和 effective domain diagnostics
- ToolPkg 错误所有权和全应用日志隐私

详细根因、revision `7` schema、候选文件、测试矩阵和禁止方案见
[现场矩阵深度分析与 revision 7 实施计划](10_20260813_field_matrix_deep_analysis_and_revision_7_plan.md)。

## 4.9 最新现场日志与 hotfix 验收边界

用户提供的 `kiyori_log_20260813_231439.txt` 在成功的
`openai_web_search:search` 调用之后记录了：

```text
evidence_parse_invalid
code=REQUIRED_FIELD_INVALID
schema_revision=7
field=all_sources
```

该证据与本地已定位的载荷错位一致：旧链路把主模型 projection 当成 UI evidence 解析。当前
hotfix 的本地验收重点是确认完整 UI `ToolResult` 可被 parser 重新解析，以及相同 binding
fingerprint 的同步兼容记录可在重启后被读取。最新日志本身不构成 hotfix APK、真实 relay 或
设备验收。
