---
status: analysis_complete
baseline_branch: main
baseline_head: 180db686aaeedfa301b3c8a31057ee4940d58649
observed_at: 2026-08-13
scope: readonly_analysis_checkpoint
---

# 8. 2026-08-13 优化调查检查点

本文件保存 `openai_web_search` 新一轮优化调查在上下文压缩前已经闭环的证据、仍待验证的假设和
下一轮入口。它是专项 TODO 的可恢复调查基线，不是实现完成记录，也不授权修改生产代码、调用真实
relay、提交或推送。

本轮用户要求研究：

- 复杂问题或并发调用时出现 `REQUEST_CANCELLED`
- 联网搜索卡片按清晰层级逐级展开和收起，完成后能够自动收起
- `MODEL_CONFIG` 选择后仍显示“配置未完成”，并评估是否只保留 `PACKAGE_ENV`
- 是否需要流式 Responses Web Search
- 是否需要改变默认 OkHttp `User-Agent`
- 继续寻找其他缺陷和优化点

## 8.1 当前进度与证据等级

当前状态：

```text
FIELD_TEST_EVIDENCE_CAPTURED
CLIENT_TIMEOUT_MISCLASSIFICATION_CONFIRMED
CONFIG_STATE_OWNERSHIP_CONFIRMED
MODEL_CONFIG_NORMAL_UI_PATH_UNREACHABLE
MODEL_CONFIG_EXISTENCE_CHECK_MISSING
OPENAI_PROVIDER_PROTOCOL_SPLIT_CONFIRMED
PROVIDER_SELECTION_SUMMARY_TRUNCATION_CONFIRMED
RESULT_CARD_STATE_SPLIT_CONFIRMED
EVIDENCE_UI_PARSE_OBSERVABILITY_GAP_CONFIRMED
STREAMING_DEFERRED_TO_BACKGROUND_MILESTONE
USER_AGENT_IMPERSONATION_REJECTED
MODEL_CONFIG_REMOVAL_SELECTED
CONCURRENCY_LIFECYCLE_AUDIT_COMPLETE
PRODUCTION_SOURCE_UNCHANGED
```

本文件使用三种证据等级：

- `已确认`：能够由当前源码、锁定依赖实现或现有自动测试直接复核
- `现场证据`：来自用户 2026-08-13 的 28 次真实调用测试，但本轮没有原始 HTTP trace、请求 ID
  或设备日志可独立重放
- `待验证`：有代码或现场现象支持，但还不能锁定唯一根因或最终产品方案

## 8.2 用户现场测试基线

以下内容是用户提供的 2026-08-13 测试摘要，不把测试报告中的根因推断自动升级为客户端事实：

- 中文实时数据、英文事实、日期纠错、最新模型、法规深度查询和天气查询成功
- `allowed_domains`、`blocked_domains`、`context_size` 和 `use_configured_location` 均至少有
  成功样本
- 空查询被参数校验拒绝；无意义字符和特殊符号能够正常处理
- 连续或并发调用期间出现过 `REQUEST_CANCELLED`，等待后简单查询恢复
- 超长多主题查询出现取消
- “对比特斯拉和比亚迪 2025 年销量并分析市场策略差异”连续五次失败；拆分后的特斯拉、
  比亚迪单主题查询和苹果与三星对比查询能够成功
- 另一个深度查询完成了多轮搜索和约 20K 输入 token，证明“复杂查询必然失败”不成立
- 成功响应中存在 `CITATION_NOT_IN_ACTION_SOURCES`；revision `5` 已把 citation annotation 与
  action sources 作为不同证据通道保留，不能把该 warning 本身当成搜索失败

由这组现场证据可以得出：

- `blocked_domains` 不是稳定触发条件
- 并发、请求持续时间、特定查询和 relay 状态都可能影响失败概率
- 不能只按“问题复杂”或“上游限流”解释所有取消
- 特定复合查询的稳定失败仍需请求级诊断，当前不能确认是 relay、模型执行、客户端超时还是
  ToolPkg 生命周期取消

用户输入中提供过真实 endpoint 和 API Key。本文件、任务日记、测试和后续实现不得复制这些凭据；
未经新的明确授权也不得调用。

## 8.3 已确认缺陷一：超时会被误分类为 `REQUEST_CANCELLED`

### 代码路径

`OpenAIHostedWebSearchGateway.executeSingleRequest()` 为同一个 `binding.timeoutSeconds` 同时设置：

```text
callTimeout
connectTimeout
readTimeout
writeTimeout
```

默认值来自 `OpenAIHostedWebSearchBindingCompiler.DEFAULT_TIMEOUT_SECONDS = 60`，manifest 同样把
`OPENAI_WEB_SEARCH_TIMEOUT_SECONDS` 默认设为 `60`。

`OpenAIHostedWebSearchGateway.awaitResponse()` 的 `onFailure` 分类顺序是：

1. `requestId in cancelledRequestIds || call.isCanceled()` → `REQUEST_CANCELLED`
2. `e is SocketTimeoutException` → `REQUEST_TIMEOUT`
3. 其他 `IOException` → `NETWORK_FAILURE`

读取成功响应体后的异常分类也先检查显式取消集合或 `call.isCanceled()`，然后才检查
`SocketTimeoutException` 或 `InterruptedIOException`。

### 根因

OkHttp `4.12.0` 的 call timeout 使用异步超时机制终止整个 call。超时退出时会取消 call，并以
`InterruptedIOException("timeout")` 表达失败。当前分类先看 `call.isCanceled()`，因此：

```text
call timeout
	→ OkHttp 取消 call
	→ call.isCanceled() == true
	→ 当前代码返回 REQUEST_CANCELLED
	→ REQUEST_TIMEOUT 分支无法到达
```

`onFailure` 的超时分支还只识别 `SocketTimeoutException`，没有覆盖 call timeout 常见的
`InterruptedIOException`。即使调整第一项，仍需统一识别 connect、read、write 和 call timeout。

### 与现场结果的关系

这是已经确认的客户端分类缺陷，能够解释“耗时较长的查询更容易显示取消”。它不能单独证明用户
看到的每一次 `REQUEST_CANCELLED` 都是 60 秒 call timeout，因为以下路径也会合法产生同一错误码：

- 用户或上层明确调用 `gateway.cancel(requestId)`
- ToolPkg execution call 结束后由 ownership registry 取消
- 父协程取消
- 等待插件级或模型级 Semaphore 时协程被取消
- bridge scope 或宿主生命周期结束

因此正式修复不能只改错误文字。需要给取消来源建立显式所有权和诊断分类，并用受控 fixture
证明每条路径。

### 实施方向

后续实现应：

1. 只以显式取消记录或协程取消原因判定用户或生命周期取消，不能用裸
   `Call.isCanceled()` 代替取消来源
2. 在取消判断前识别 OkHttp call、connect、read 和 write timeout，统一映射
   `REQUEST_TIMEOUT`
3. 为 `REQUEST_CANCELLED` 增加有界、非秘密的取消阶段与 owner，例如排队、已发送、读响应体、
   execution call 结束或用户停止
4. 为 timeout 增加 elapsed、configured timeout 和请求阶段，不记录 prompt、Key 或完整 headers
5. 新增 MockWebServer 测试，分别覆盖 call timeout、read timeout、显式取消、父协程取消和排队取消
6. 保持当前不自动重试、不切换 endpoint、模型、Key 或搜索后端的合同

## 8.4 并发、排队与生命周期的当前结论

### 已确认结构

搜索执行依次经过：

```text
插件 requests-per-minute limiter
	→ MODEL_CONFIG requests-per-minute limiter
	→ 插件级 Semaphore
	→ MODEL_CONFIG 级 Semaphore
	→ 单次 OkHttp call
```

插件默认 `maxConcurrentRequests = 1`，允许范围是 `1..8`。`MODEL_CONFIG` 模式还会叠加模型配置
自己的并发和每分钟限制。等待 Semaphore 时若协程取消，`withConcurrencyLimits()` 直接包装为
`REQUEST_CANCELLED`，当前结果没有说明是否已经发送 HTTP 请求。

`RequestConcurrencyRegistry` 会在同一 key 的配置并发值发生变化时替换 registry 中的
Semaphore。已经取得旧 Semaphore 引用的执行仍继续使用旧实例，因此配置热切换期间可能暂时存在
新旧两个并发预算。该行为是否会造成用户现场的失败尚未验证，但必须进入并发测试。

### 已确认：execution owner 移除会取消所属搜索

`ToolPkgOpenAIWebSearchBridge.search()` 在 dispatch 前建立：

```text
requestId
	→ callId
	→ gateway Call 与 bridge Job 的取消动作
```

`JsEngine.removeExecutionSession(callId)` 只要实际移除了 session，就调用：

```text
openAIWebSearchBridge.cancelForCall(
	callId,
	"OpenAI Web Search execution owner completed."
)
```

该设计的目标是保证搜索请求不能脱离创建它的 ToolPkg execution 存活，尤其防止脚本启动异步搜索
后不等待 Promise 就返回。但它也意味着以下不同结束原因会沿同一 ownership 路径取消请求：

- 工具正常返回
- 工具抛错
- ToolPkg 脚本等待超时
- 聊天停止
- execution dispatch 失败
- JS Promise 没有正确等待搜索 callback 就提前 settle

当前正常结果回调 `completeCallFuture()` 的顺序还是：

```text
removeExecutionSession(callId)
	→ cancelForCall(callId)
	→ future.complete(result)
```

搜索 bridge 自身在准备投递结果前会先执行 `requestRegistry.complete(requestId)`。如果该清理已经
完成，随后移除 execution owner 不会再找到活动搜索；这是正常完成路径能够安全工作的前提。但以下
顺序仍需要受控测试，而不能仅由静态阅读宣称不存在竞态：

```text
gateway 完成
	→ requestRegistry.complete(requestId)
	→ deliverResult 入 QuickJS 队列
	→ JavaScript callback 解析并 resolve Promise
	→ ToolPkg setCallResult
	→ removeExecutionSession(callId)
```

需要重点证明：

- callback 投递排队期间 execution session 不会被其他完成路径提前移除
- JS 包装层真正 `await` 了搜索 Promise，而不是在收到 started envelope 后提前返回
- 同一 call 启动多个搜索时，一个搜索完成不会让整个 owner 提前结束并取消其他搜索
- `requestRegistry.complete()` 与 `cancelForCall()` 竞态不会覆盖已经生成但尚未投递的结果
- ToolPkg timeout 与 HTTP timeout 分别使用什么预算，谁先到期，以及最终错误码由谁所有

因此 `REQUEST_CANCELLED` 不能只解释为 HTTP 层取消。正式诊断至少需要区分：

```text
USER_CANCELLED
CHAT_STOPPED
EXECUTION_OWNER_COMPLETED
EXECUTION_TIMEOUT
QUEUE_CANCELLED
BRIDGE_CLOSED
HTTP_TIMEOUT
```

这些可以作为内部取消原因或阶段，不要求全部变成新的公开错误码；但公开
`REQUEST_CANCELLED` 与 `REQUEST_TIMEOUT` 必须准确，诊断字段必须足以复核来源。

### 检查点创建时的待验证问题

- rate limiter 等待是否包含在用户感知的总执行时限内
- ToolPkg 脚本执行 timeout 是否可能先于 Web Search 的 60 秒 HTTP timeout
- execution call 在 JavaScript Promise 等待期间是否被过早标记结束
- 同一消息中的多个 ToolPkg call 是否共享正确的 ownership key
- 插件 Semaphore 和模型 Semaphore 的获取顺序是否会形成长排队或配置切换期间超发
- 请求完成和 `cancelCall()` 竞态是否会把已完成结果覆盖成取消
- 当前可观察数据能否区分“排队时取消”和“HTTP 已发送后取消”

后续静态审计已经确认 ToolPkg 与 HTTP 的预算优先级、JavaScript Promise 等待最终 callback、
execution owner 清理顺序、ownership key 和配置热变更时旧 gate 继续存活等事实，见
[8.13 后续调查收敛结果](#813-后续调查收敛结果)。需要新增 MockWebServer fixture、竞态测试或
生产诊断字段才能证明的项目已经转入
[优化实施计划的测试矩阵](9_20260813_optimization_implementation_plan.md#913-测试矩阵)，本轮没有
修改或运行这些实现级测试。

在这些问题完成受控测试前，不能加入自动重试。尤其 HTTP 请求已经发送但响应头尚未到达时，
客户端无法仅凭本地异常证明远端没有受理或计费。

## 8.5 已确认缺陷二：`MODEL_CONFIG` 的产品语义与 UI 不一致

### 当前真实合同

`MODEL_CONFIG` 不跟随主聊天页当前选中的模型。它要求：

1. `OPENAI_WEB_SEARCH_CONFIG_SOURCE = MODEL_CONFIG`
2. `OPENAI_WEB_SEARCH_MODEL_CONFIG_ID` 是一个存在且稳定的模型配置 ID
3. 配置 provider 类型满足 official 或 relay strict 合同
4. 配置中存在可用 API Key
5. `OPENAI_WEB_SEARCH_MODEL` 精确出现在该配置的 `modelName` 列表中
6. relay strict 时当前完整指纹已通过兼容探测

`OpenAIHostedWebSearchBindingResolver` 读取的是 ToolPkg host environment 和
`ModelConfigManager.getModelConfig(modelConfigId)`，没有读取主聊天页面的当前选择。代码还显式输出
`chat_provider_independent = true`。

因此“主模型已选 GPT-5.6，但插件仍显示配置未完成”与当前内部合同并不矛盾。真正缺陷是产品入口
没有让用户建立正确心智模型，也没有提供选择稳定配置 ID 的可靠控件。

### 当前 UI 缺口

- `ModelConfigManager.createConfig()` 使用 `UUID.randomUUID().toString()` 生成 `config.id`
- `ModelConfigSummary` 和内部 `list_model_configs` 工具可以携带该 ID，但“模型与参数配置”页面只用
  ID 做内部状态 key，界面展示配置名称，不显示、不复制也不导出稳定 ID
- manifest 把 `OPENAI_WEB_SEARCH_MODEL_CONFIG_ID` 暴露为自由文本
- 通用 ToolPkg 环境变量输入类型只有 `text`、`password`、`enum`、`boolean`、`number` 和
  `json`，没有能够动态读取 `ModelConfigManager` 的模型配置引用类型
- OpenAI Web Search 设置页没有从 `ModelConfigManager` 投影可选配置列表
- `getStatus()` 解析失败时设置页只把 status 清空并显示笼统“配置未完成”
- 具体的 `MODEL_CONFIG_NOT_FOUND`、`MODEL_NOT_IN_CONFIG`、`PROVIDER_NOT_RESPONSES`、
  `API_KEY_MISSING` 等原因只进入 error 字符串，没有形成字段级就绪状态和修复动作
- 当前绑定详情只在完整 resolve 成功后显示；失败时用户看不到是“配置 ID 不存在”还是“搜索模型
  不在该配置”
- 主聊天模型与搜索绑定独立的规则虽然有说明文字，但不能代替可选择、可验证的配置交互

因此当前 `MODEL_CONFIG` 在正常用户 UI 流程中是不可达的：

```text
模型与参数配置
	创建配置
	→ 内部生成 UUID
	→ UI 只显示配置名称

插件环境变量
	要求手工输入 UUID
	→ 没有选择器
	→ 没有复制入口
	→ 用户无法完成绑定
```

内部 `list_model_configs` 工具可以暴露 `config_id`，但这只说明数据存在，不能把“让用户先调用一个
软件设置工具，再复制内部 UUID”当成产品方案。插件配置必须在自身 UI 中闭环。

### 已确认：当前没有严格的配置 ID 存在性查询

`OpenAIHostedWebSearchBindingResolver` 当前通过：

```kotlin
modelConfigManager.getModelConfig(modelConfigId)
	?: MODEL_CONFIG_NOT_FOUND
```

读取配置。但 `ModelConfigManager.loadConfigFromDataStore()` 在以下两种情况下都不会对非默认 ID
返回 `null`：

- `config_<id>` 键不存在
- 对应 JSON 存在但解析失败

它会合成：

```kotlin
ModelConfigData(
	id = configId,
	name = context.getString(R.string.model_config_config_id, configId),
)
```

默认 ID `default` 缺失或损坏时则合成一份 fresh default config。由此产生的实际错误链是：

```text
用户输入不存在或已删除的 UUID
	→ ModelConfigManager 合成同 ID 的默认字段对象
	→ BindingResolver 的 MODEL_CONFIG_NOT_FOUND 分支基本不可达
	→ 后续被报为 PROVIDER_NOT_RESPONSES、MODEL_NOT_IN_CONFIG 或 API_KEY_MISSING
```

这不仅影响 Web Search，也说明 `getModelConfig()` 当前同时承担了两种互斥语义：

```text
宽松读取：缺失时构造可继续编辑的对象
严格引用：被其他功能绑定时必须证明 ID 真实存在且数据可解析
```

正式实现不应继续用宽松读取承担稳定引用校验。需要增加明确的严格查询，例如：

```text
configListFlow 包含该 ID
	→ config_<id> 键存在
	→ JSON 可成功解析
	→ 解析后的 config.id 与请求 ID 一致
```

失败时分别返回稳定的 `MODEL_CONFIG_NOT_FOUND` 或 `MODEL_CONFIG_INVALID`。配置列表、键和值不一致
属于数据完整性问题，不能自动合成配置、自动改绑或静默选择其他模型。配置删除后，已绑定的
OpenAI Web Search 应显示明确失效状态，等待用户重新选择。

### 如果保留 `MODEL_CONFIG`，首选交互

首选方案是让 OpenAI Web Search 设置页直接提供宿主管理的两级选择器：

```text
配置来源
	复用模型配置

模型配置
	配置名称
	provider 类型
	endpoint 的安全标签
	可用 Key 状态

搜索模型
	从所选配置的 modelName 列表中选择精确模型
```

宿主内部仍保存稳定 `config.id`，但不要求用户读取或输入 UUID。为避免创建第二份状态源：

- `ModelConfigManager` 继续是模型配置列表和内容的唯一 owner
- ToolPkg host environment 只保存所选稳定 ID 与精确模型名
- OpenAI Web Search 的 package-bound host service 提供脱敏的可选配置摘要和绑定写入操作
- 配置删除、provider 改变或模型被移除后，状态页显示精确失效原因并要求重新选择，不自动改绑
- Key、Key pool、完整 headers 和其他敏感字段不进入 ToolPkg JavaScript
- 通用环境变量抽屉不再把内部 UUID 作为普通文本字段要求用户填写

候选实现有两条：

1. 为 ToolPkg 环境变量合同新增通用的 `MODEL_CONFIG_REFERENCE` 动态输入类型，由环境变量抽屉调用
   `ModelConfigManager`。它可被其他插件复用，但会扩大通用 manifest、UI、序列化和权限边界
2. 在 `openai_web_search` package-bound host service 中增加专用的配置列表与绑定操作，由插件
   设置页完成选择。它影响面更小，也能同时提供第二级精确模型选择

当前倾向第二条。原因是这个绑定不仅需要一个配置 ID，还必须校验 provider contract、可用 Key、
精确模型名和 relay probe 状态；把它压缩成通用单字段选择器仍无法完成完整交互。最终选择要在
下一轮审计 ToolPkg UI 写入 host-only environment 的既有边界后冻结。

### package-bound 配置写入链审计结论

现有通用环境变量抽屉由 Android 宿主直接读写 `ToolPkgHostEnvironmentRepository`：

```text
PackageManagerScreen
	→ PackageEnvironmentVariablesSheet
	→ ToolPkgHostEnvironmentRepository.setValue(container, variable, value)
```

`ToolPkgHostEnvironmentRepository` 使用 app-private `SharedPreferences`，要求 container identity
和 variable name；它没有 JavaScript bridge。`PACKAGE + HOST_SERVICE` 变量也不会进入普通
ToolPkg `getEnv()`，因此当前 API Key 隔离边界是正确的。

OpenAI Web Search 的 Compose DSL 设置页则通过 package-bound
`ToolPkg.services.openAIWebSearch` 调用宿主。现有服务只有：

```text
getStatus
validateLocalConfiguration
runCompatibilityProbe
search
cancel
```

没有列出模型配置或保存绑定的操作。Compose DSL action 已携带：

```text
containerPackageName = com.kiyori.openai_web_search
runtimeKind = ui
active execution call ID
```

现有 compatibility probe 已用这些字段限制为绑定包的设置 UI 调用。因此在不扩大通用
JavaScript 权限的前提下，可以为同一专用服务新增本地操作：

```text
listEligibleModelConfigs()
getBindingDraft()
saveModelConfigBinding(request)
```

这三个操作必须继续验证：

- 调用方 container 必须精确是 `com.kiyori.openai_web_search`
- execution call 必须仍然 active
- 保存操作只允许 `runtimeKind = ui`
- 请求字段使用固定 schema，不允许提交任意环境变量名
- 返回只含脱敏摘要，不含 API Key、Key pool 内容、完整 headers 或其他配置秘密

`listEligibleModelConfigs()` 应由宿主严格读取真实存在且可解析的配置，并返回：

```text
id
display_name
provider_type
provider_contract_eligibility
endpoint_authority 或安全 host 标签
exact_models
has_usable_key
```

`id` 可以作为不直接展示的稳定选择值返回给受信页面逻辑；用户界面显示名称和协议摘要，不要求用户
阅读 UUID。模型列表必须复用 `parseModelNameInput()` 的精确、保序、去重语义，不做大小写折叠、
模型别名推断或自动选择。

### 必须原子保存“来源 + 配置 ID + 模型”

当前通用环境变量抽屉对 package-scoped 值逐字段调用
`ToolPkgHostEnvironmentRepository.setValue()`。每次调用都会创建独立
`SharedPreferences.Editor` 并 `apply()`。对于普通独立变量可以接受，但以下绑定是一个不可拆分的
逻辑事务：

```text
OPENAI_WEB_SEARCH_CONFIG_SOURCE = MODEL_CONFIG
OPENAI_WEB_SEARCH_MODEL_CONFIG_ID = <真实配置 ID>
OPENAI_WEB_SEARCH_MODEL = <该配置中的精确模型>
OPENAI_WEB_SEARCH_PROVIDER_CONTRACT = <与配置相容的合同>
```

如果逐字段保存，同时有搜索、状态读取或 compatibility probe，resolver 可能观察到半套新旧绑定，
产生瞬时 `MODEL_NOT_IN_CONFIG`、错误 endpoint 合同或错误 compatibility fingerprint。

正式实现需要在 repository 增加受限批量事务，例如一次
`SharedPreferences.Editor` 完成固定字段的 put/remove，并在同一个宿主临界区内：

1. 严格加载并校验目标配置
2. 校验 provider 与所选 contract
3. 校验精确模型属于配置
4. 校验存在可用 Key
5. 一次提交全部绑定字段
6. 重新解析刚保存的绑定并返回 sanitized status

不能暴露通用 `setHostEnvironment(name, value)` 给 JavaScript，也不能允许 UI 直接写 Key、
endpoint 或 headers。保存失败不得留下部分字段。

### `MODEL_CONFIG` 与 `PACKAGE_ENV` 的设置 UI 必须分权

Compose DSL 当前暴露的 `ctx.getEnv()`、`ctx.setEnv()` 和 `ctx.setEnvs()` 是 JavaScript 可见的普通
环境变量能力，不能用于 `PACKAGE + HOST_SERVICE` 配置。尤其 API Key、认证和完整 headers 不能
经由该路径进入插件运行时。

因此如果保留双来源，设置页职责应是：

```text
MODEL_CONFIG
	插件设置页可列出脱敏模型配置
	插件设置页可提交受限的配置 ID + 精确模型 + contract
	宿主完成严格校验与原子保存

PACKAGE_ENV
	插件设置页只显示脱敏 readiness 和当前安全摘要
	敏感 endpoint/Key/auth/headers 仍由原生宿主受信编辑器管理
	可提供明确导航动作打开该插件的原生配置分组
```

不要在 Compose DSL 中复制一套密码输入和 host-only 存储写入；那会形成第二套凭据 UI 与权限边界。
如果未来需要插件详情页内编辑 `PACKAGE_ENV`，应由原生宿主渲染受信控件，而不是把值交给
TypeScript。

### 字段级 readiness schema

当前 `getStatus()` 只有完整 resolve 成功才返回 status，失败时 UI 把 status 清空，导致所有错误都
显示为“配置未完成”。正式服务应把“读取配置状态”和“要求完整可执行绑定”分开：

```text
getConfigurationReadiness()
	→ 总是返回结构化检查项

validateLocalConfiguration()
	→ 仅在所有本地检查通过时 success

getStatus()
	→ 返回已解析的完整有效绑定
```

建议 readiness 至少包含：

| 检查项 | 典型状态 |
| --- | --- |
| config source | `ready` / `invalid` |
| model config reference | `not_required` / `missing` / `not_found` / `invalid` / `ready` |
| provider protocol | `not_responses` / `official` / `responses_compatible` |
| endpoint contract | `missing` / `invalid` / `official` / `relay` |
| exact model | `missing` / `not_in_config` / `not_allowed` / `ready` |
| credential | `missing` / `unavailable` / `ready` |
| local request options | `invalid` / `ready` |
| compatibility probe | `not_required` / `missing` / `stale` / `failed` / `valid` |

每项返回稳定 code、短说明和明确修复动作，但不得返回秘密。这样 `modelConfigId` 不存在、Provider
协议错误、模型不属于配置和 Key 不可用不会再被压成同一个文案。

### 检查点阶段的 `MODEL_CONFIG` 取舍

检查点创建时还不能直接决定删除。

保留它的真实价值是：

- 复用现有 endpoint、Key pool、headers、并发和速率限制
- 不在插件环境中复制一份 Key
- Key 轮换与 availability 状态继续由 `ModelConfigManager` 持有

它的当前成本是：

- 正常 UI 无法取得或填写内部 UUID，当前路径实质不可用
- 用户必须同时理解模型配置、稳定 ID 和精确搜索模型名
- ToolPkg 通用环境变量 UI 不适合展示跨仓库对象选择器
- 主聊天模型与搜索模型名字相同会进一步制造“应该自动跟随”的错觉
- 两种来源增加状态、测试、文档和迁移复杂度

当时需要先回答“这个接口是否已发布并需要兼容”。如果保留 `MODEL_CONFIG`，修复标准不是补一段
说明或显示 UUID，而是完成上述配置与模型选择器。如果未发布且产品决定只保留独立搜索配置，
可以完整移除 `MODEL_CONFIG` 的 manifest 字段、resolver 分支、Key selector、双重 limiter、
状态输出、UI 和测试，`PACKAGE_ENV` 成为唯一来源而不是保留不可达旧代码。如果已发布，则必须设计
显式迁移和兼容窗口，不能直接破坏已有绑定。

后续已经由项目权威 `CONTEXT.md` 确认 Kiyori 尚未有用户发布版本，并完成本插件使用面审计。
最终决策是 revision `6` 只保留 `PACKAGE_ENV`，完整删除本插件的 `MODEL_CONFIG` 专项链；不新增
UUID 展示、两级选择器或兼容窗口。实施范围见
[配置架构：只保留 PACKAGE_ENV](9_20260813_optimization_implementation_plan.md#96-配置架构只保留-package_env)。

### 四种 OpenAI Provider 的协议边界

当前模型配置界面中的四个相关选项对应四个持久化枚举值：

| 用户可见名称 | `ApiProviderType` | 当前 Provider 实现 | endpoint 语义 |
| --- | --- | --- | --- |
| OpenAI（GPT 系列） | `OPENAI` | `OpenAIProvider` | 官方 Chat Completions |
| OpenAI 通用 | `OPENAI_GENERIC` | `OpenAIProvider` | 自定义或兼容 Chat Completions |
| OpenAI 官方（Responses API） | `OPENAI_RESPONSES` | `OpenAIResponsesProvider` | 官方 Responses |
| Responses 兼容端点 | `OPENAI_RESPONSES_GENERIC` | `OpenAIResponsesProvider` | 自定义或中转 Responses |

`AIServiceFactory` 明确把 `OPENAI` 与 `OPENAI_GENERIC` 路由到 `OpenAIProvider`，把
`OPENAI_RESPONSES` 与 `OPENAI_RESPONSES_GENERIC` 路由到 `OpenAIResponsesProvider`；
`EndpointCompleter` 也只对后两者补全 Responses endpoint。

对 `openai_web_search` 而言：

- `OPENAI` 和 `OPENAI_GENERIC` 是 Chat Completions 协议，不能满足 hosted Web Search 的
  Responses 合同
- official 合同只接受 `OPENAI_RESPONSES`
- relay strict 合同接受 `OPENAI_RESPONSES` 或 `OPENAI_RESPONSES_GENERIC`，但仍要求当前精确
  绑定通过真实兼容探测

因此不能仅因四个名称相近就直接删除或合并。它们至少表达两个不同 API 协议，以及官方 endpoint
与自定义 endpoint 两个边界。下一轮仍需完整审计：

- 默认 endpoint 和 endpoint 补全
- 模型列表获取与测试请求
- Key 校验和 Key pool
- Responses sequence、流恢复或其他协议专属状态
- 已持久化枚举值、导入导出和迁移
- 所有 Provider 消费者、文档和测试

在使用面审计完成前，优先优化名称、分组和说明，不把 Provider 枚举删减与 Web Search 的
`MODEL_CONFIG` 产品取舍混为同一件事。

### 已确认：Provider 已选值被摘要行强制截断

Provider 选择对话框 `ApiProviderDialog` 能显示完整 `displayName`；问题发生在选择后的
`SettingsSelectorRow`。该行把已选值设置为：

```kotlin
Modifier.weight(0.5f, fill = false)
maxLines = 1
overflow = TextOverflow.Ellipsis
```

左侧还同时显示标题与副标题，因此“OpenAI 官方（Responses API）”等较长名称在窄屏或大字体下
必然出现省略号。根因不是 Provider 名称丢失，也不是选择没有保存，而是摘要布局没有给已选值足够
空间。

正式 UI 不应只增大一个固定 weight。更稳妥的层级是：

```text
第一行：API 提供商 + 下拉图标
第二行：完整 Provider 名称，允许两行
第三行：协议说明或 endpoint 类型，可选
```

如果继续使用横向结构，也必须允许已选值占据剩余完整宽度并支持至少两行，同时覆盖本地化长文本、
动态 ToolPkg Provider 名称、系统字体放大和窄屏。需要 Compose UI 测试验证完整语义可读，而不是
只比较截图。

## 8.6 已确认缺陷三：搜索结果卡片缺少统一的分层折叠状态

### 当前结构

`ThinkToolsXmlNodeGrouper` 管理外层“思考与工具”或“工具调用”分组：

- 流式阶段且组仍位于消息尾部时自动展开
- 流结束或用户取消并落为静态消息后自动收起
- 用户点击后设置 `userOverride`，不再被自动状态覆盖
- `ToolCollapseMode` 决定哪些工具进入外层组

`OpenAIWebSearchToolResultDisplay` 在内层独立持有：

```text
detailsExpanded = false
```

但主卡片、答案和所有 `sources` 始终展开。只有模型、evidence mode、usage、search actions、
warnings 和 citation 数量位于“搜索证据详情”开关下。

这形成两个互不协调的状态层：

```text
外层工具组
	└─ OpenAI Web Search 结果卡
		├─ 查询
		├─ 完整答案
		├─ 全部来源，始终展开
		└─ 搜索证据详情，可展开
```

单个搜索工具不一定被外层分组收起，因为 `ToolCollapseMode.READ_ONLY` 和 `ALL` 当前要求至少
两个工具节点才建立 group。因此长答案和大量来源在单工具场景会持续占据聊天高度。

### 建议的层级

正式 UI 方案应由一个结果卡状态模型管理三层，而不是继续添加互不知情的 Boolean：

```text
L0 工具组
	消息级“思考与工具”，沿用 ThinkToolsXmlNodeGrouper

L1 搜索结果摘要
	状态、查询摘要、来源数、搜索动作数、耗时或用量摘要
	完成后默认收起，执行中展开

L2 搜索答案
	带来源标记的答案正文
	由 L1 展开后显示，默认展开

L3 证据分区
	来源
	搜索轨迹
	诊断与用量
	三个分区分别可收起，默认收起
```

交互规则建议：

- 执行中：外层组展开，L1 展开，显示进行状态；尚无完成证据时不显示空分区
- 成功完成：保留一帧完成状态后，由外层现有机制统一自动收起；用户已手动操作时尊重
  `userOverride`
- 用户展开外层组：看到紧凑 L1 摘要，不立即展开全部来源
- 用户展开 L1：答案正文出现，来源、轨迹、诊断仍各自收起
- 来源标题显示数量和 URL 或 structured feed 概要；点击后展开来源列表
- 搜索轨迹显示动作数；展开后按 search、open_page 和 find_in_page 分组或使用紧凑时间线
- 诊断与用量属于高级信息；warnings 只在存在时提高视觉优先级
- 单一来源和零来源也使用同一层级，不写特殊旁路
- 旋转、列表回收和消息重组时，状态 key 至少包含稳定消息或 render key 与 request ID，避免不同
  搜索结果复用状态

还需要审计无障碍语义、动画高度变化、长 URL、结构化 feed、失败结果、来源数量很大和嵌套卡片
padding。UI 实施前应先给状态转换写纯 Kotlin 测试，再做 Compose UI 测试。

## 8.7 已确认缺陷四：专用 evidence UI 解析失败被静默降为普通结果

`OpenAIHostedWebSearchEvidenceParser.parseOrNull()` 对目标工具执行：

```text
runCatching { parse(resultJson) }.getOrNull()
```

没有日志、错误类型或诊断回调。`CustomXmlRenderer` 得到 `null` 后继续使用通用
`ToolResultDisplay`。因此 schema revision、source diagnostics 或 JSON 结构回归时，用户看到的
表现只是专用搜索卡消失，开发者也没有可定位日志。

后续应把结果改为显式解析状态：

```text
NotApplicable
Parsed(evidence)
Invalid(errorCode, sanitizedSummary)
```

`Invalid` 仍可显示通用原始结果入口，但必须：

- 记录一次有界、脱敏的 parser 诊断
- 在开发或诊断信息中区分 schema mismatch、字段缺失、类型错误和 source invariant 失败
- 不记录完整用户查询、完整答案或完整来源 JSON
- 增加 renderer 路由测试，证明目标工具解析失败不会静默消失

## 8.8 流式支持的当前判断

当前 request compiler 固定：

```text
tool_choice = required
include = web_search_call.action.sources
store = false
stream = false
```

当前 gateway、response parser、ToolPkg service bridge、XML 结果持久化和 evidence card 都以“一次
完成 JSON”作为合同。引入流式不是把 `stream` 改成 `true`，还至少需要：

- SSE event parser 和事件顺序状态机
- partial output、web search action 和最终 sources 的合并规则
- 中断发生在首个事件前或部分事件后的提交与持久化语义
- ToolPkg bridge 的增量事件协议
- 聊天 XML 或消息存储中的 partial/final ownership
- 取消后保留部分答案还是只保留诊断的产品决策
- relay 对流式 citation 与 `action.sources` 通道差异的兼容矩阵

用户现场测试说明非流式能够完成 20K 到 43K 输入 token 的深度搜索；当前取消又至少包含客户端
超时误分类。因此“改成流式可以修复 `REQUEST_CANCELLED`”没有证据。

检查点阶段倾向继续保持非流式，先修复错误分类、执行预算和取消来源诊断。后续官方合同核对已经
确认 streaming 是 SSE 增量传输，而可恢复长任务、轮询和服务端 cancel 属于 background
生命周期。最终仍保持同步非流式作为当前修复目标，background 与可续流作为独立里程碑，见
[流式与 background 后续里程碑](9_20260813_optimization_implementation_plan.md#911-流式与-background-后续里程碑)。

## 8.9 `User-Agent` 的当前判断

当前 Web Search gateway 没有显式设置 `User-Agent`，因此会使用 OkHttp 默认标识。用户提出改成
Codex Desktop 风格字符串以避免封号，但目前没有证据表明：

- OpenAI 官方要求模拟 Codex Desktop
- 当前 relay 因 `okhttp/4.12.0` 封禁或限流
- 伪造另一个产品身份会改善稳定性

模拟 Codex Desktop 还会错误表达实际客户端身份，并可能干扰 relay 风控、审计和问题定位。当前
不建议伪造第三方产品字符串。

后续 OpenAI 官方资料核对没有发现模拟 Codex Desktop 的要求，也没有取得 relay 一手证据证明
OkHttp 默认标识是取消或限流根因。最终方案拒绝伪造其他产品身份，并把 `user-agent` 加入 extra
headers 禁止覆盖名单；relay 将来若提出正式要求，再单独评审专用字段和兼容探测。见
[`User-Agent` 与 headers](9_20260813_optimization_implementation_plan.md#910-user-agent-与-headers)。

## 8.10 其他已发现优化点

### P1：错误分类与 retryable 语义脱节

`REQUEST_TIMEOUT`、`NETWORK_FAILURE` 和部分 HTTP 错误被标记 `retryable=true`，但产品严格禁止
自动重试。需要明确该字段表示“允许用户发起新请求”还是“宿主可以重试”。如果没有唯一消费者，
应重命名或补合同，避免未来误加自动重复 POST。

### P1：一个超时值同时控制四种网络阶段

connect、write、read 和总 call 使用同一个秒数，无法表达“快速连接失败”和“允许深度搜索长时间
生成”的不同预算。下一轮应测量真实阶段后决定是否拆分；不得仅把默认值从 60 提高到 300 掩盖
分类缺陷。

### P0：`MODEL_CONFIG` 没有可完成的 UI 配置路径

内部生成并要求稳定 UUID，但模型配置页不显示该 ID，环境变量页又只有普通文本输入。应优先决定
完整删除该来源，或实现宿主管理的配置与精确模型选择器。仅增加 UUID 复制按钮可以用于诊断，但
不能作为最终产品交互。

### P1：状态页失败投影不足

设置页需要展示结构化就绪检查清单，而不是只有成功后的完整 status 或失败时的一个 error 字符串。
至少应覆盖配置来源、固定配置、provider、模型、Key、endpoint、local validation 和 relay probe。

### P1：未知模型配置 ID 被宽松读取掩盖

当前不存在或损坏的配置会被合成 `ModelConfigData`，使 `MODEL_CONFIG_NOT_FOUND` 失真。应增加
严格存在性和完整性查询，并把缺失与损坏分开显示；不得自动创建、自动迁移或自动改绑。

### P2：Provider 摘要布局隐藏所选项

已选 Provider 被单行、半权重和省略号组合截断。应改为可完整阅读的纵向摘要或多行布局，并覆盖
窄屏、大字体、本地化和动态 Provider 名称。

### P2：来源列表缺少规模控制

当前对 `evidence.sources` 全量 `forEach`，长查询可能产生几十个来源并一次性组合。需要评估
LazyColumn 嵌套限制、首批预览、展开后的列表策略和来源去重展示，但不得丢失完整证据数据。

### P2：搜索动作展示未按类型组织

当前只把动作字段拼成一行。搜索、打开页面和页内查找的视觉语义不同，建议在展开区按动作类型显示
图标、主字段和次要字段，并对长 URL 做域名优先的紧凑展示。

### P2：warning 的用户级与诊断级语义混合

`CITATION_NOT_IN_ACTION_SOURCES` 是跨通道差异诊断，不等于答案无引用。需要把“影响证据可靠性”
和“仅说明 relay 未返回完整审计通道”的 warning 分级，避免所有 warning 都使用 error 色。

## 8.11 检查点后的调查清单与转交状态

以下列表是检查点创建时的后续入口。源码、UI、配置使用面和官方协议的静态调查已经完成；需要新增
fixture、测试源码、生产诊断、Compose UI 测试或 APK 的项目已转入实施蓝图，本轮没有执行：

1. 为 OkHttp timeout 与显式 cancel 建立最小受控测试，确认 `onFailure` 的实际异常类型、
   `call.isCanceled()` 和请求阶段
2. 追踪 ToolPkg JavaScript Promise、host service callback、execution call registry、
   `JsToolManager` timeout 和消息取消之间的完整时序
3. 为不存在、已删除和损坏的模型配置增加只读测试设计，冻结严格引用查询语义与错误码
4. 盘点 `MODEL_CONFIG` 的真实使用面和发布状态，确认 package-bound 设置 UI 是否可以安全列出
   脱敏配置摘要并写入 host-only 绑定
5. 把配置失败原因整理成字段级 readiness schema，并比较“专用两级选择器”与“完整移除”的迁移
   成本；不把手工复制 UUID列为正式方案
6. 完成四种 OpenAI Provider 的使用面和持久化审计；在此之前只冻结协议分组，不决定删除枚举
7. 为 Provider 摘要行设计窄屏、大字体和长名称 Compose 验收矩阵
8. 盘点其他专用工具卡和 `ToolCollapseMode` 行为，冻结搜索卡三层状态模型、自动收起规则和 UI
   验收矩阵
9. 核对 OpenAI 官方 Web Search streaming、background response、cancel、sources/citations 合同
10. 核对官方和 relay 对 `User-Agent` 的要求；没有一手证据则维持不伪造
11. 完成问题优先级、候选文件、测试矩阵、迁移与发布兼容方案后，再等待用户授权正式编码

建议优先阅读：

```text
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/
	OpenAIHostedWebSearchGateway.kt
	ToolPkgOpenAIWebSearchBridge.kt
	OpenAIHostedWebSearchBindingResolver.kt
	RequestConcurrencyRegistry.kt
	OpenAIHostedWebSearchRequestCompiler.kt
	OpenAIHostedWebSearchEvidence.kt

app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/part/
	ThinkToolsXmlNodeGrouper.kt
	CustomXmlRenderer.kt
	OpenAIWebSearchToolResultDisplay.kt
	ToolResultDisplay.kt

examples/openai_web_search/
	manifest.json
	src/shared.ts
	src/ui/index.ui.ts
	src/packages/openai_web_search.ts
```

## 8.12 本检查点创建时未完成的内容

创建本检查点时：

- 未修改生产代码或测试
- 未执行真实 relay、OpenAI API、设备或模拟器请求
- 未决定删除或保留 `MODEL_CONFIG`
- 未冻结最终 UI 视觉稿和所有状态转换
- 未决定引入流式
- 未决定显式 Kiyori `User-Agent`
- 未跑实现验证或 Debug APK 构建
- 未提交或推送

2026-08-13 的后续调查已经完成本文件列出的关键源码、UI 和官方协议核对。最终决策、候选文件、
实施顺序、测试矩阵与验收门禁已转入
[优化实施计划](9_20260813_optimization_implementation_plan.md)。

本文件现在是调查证据历史，专项状态为 `LOCAL_ANALYSIS_COMPLETE`。生产源码、测试源码、ToolPkg
manifest 与构建产物仍未修改；实现、构建、设备、relay 和用户验收均未开始。

## 8.13 后续调查收敛结果

本轮继续调查后确认：

- `openai_web_search:search` 的 TypeScript 包装层使用
  `return await ToolPkg.services.openAIWebSearch.search(request)`；宿主生成的 Service Promise
  只有收到最终 callback 后才 resolve 或 reject，不会把 started envelope 当成工具完成结果
- 普通 PackageTool 调用通过 `JsToolManager.executeScript()`，JavaScript 主执行预算为
  `1800` 秒；当前 Web Search 默认 HTTP call 预算为 `60` 秒，因此正常聊天搜索通常先到达
  HTTP 预算，而不是 ToolPkg 脚本预算
- 搜索 worker 在生成结果后先调用 `requestRegistry.complete(requestId)`，再把 callback
  排入 QuickJS；execution session 只有在工具 Promise 最终 settle 后才完成
- 当前 ownership registry 的取消请求只读取 active entry 并调用取消动作，只有
  `complete()` 才移除 entry。取消与完成没有原子终态领取，现有测试还允许“取消后继续
  complete”，因此需要显式生命周期状态机
- rate limiter 与 Semaphore 等待发生在 OkHttp call 创建前，不计入当前 `60` 秒 HTTP
  call timeout，但会消耗 `1800` 秒 ToolPkg 总预算。并发配置变化时 registry 会替换 gate，
  已持有旧 gate 的请求继续使用旧实例
- 项目权威 `CONTEXT.md` 明确 Kiyori 尚未有用户发布版本。`MODEL_CONFIG` 又只在本插件专项链中
  形成不可达配置路径，因此正式方案选择完整移除该来源，而不是新增 UUID 展示、选择器或迁移窗口
- 四种 OpenAI Provider 枚举有广泛持久化、模型列表、endpoint、Provider factory、聊天与测试
  使用面，不能随 Web Search 的 `MODEL_CONFIG` 一并删除；只优化名称分组和已选值可读性
- OpenAI 官方 Web Search 文档确认完整 `sources` 与行内 `url_citation` 是不同输出通道；复杂
  agentic search 会增加延迟，长研究任务建议使用 background mode
- OpenAI 官方 streaming 文档定义的是 SSE 语义事件；background mode 才提供异步状态、轮询、
  可续流 sequence cursor 和服务端 response cancel。仅 background response 可以调用
  `/responses/{response_id}/cancel`
- 当前取消问题不通过把 `stream=false` 改成 `true` 处理。同步非流式先完成错误分类、预算、
  admission 和 lifecycle 修复；background/可续流另立里程碑
- OpenAI 官方资料没有要求模拟 Codex Desktop `User-Agent`。正式方案拒绝伪造其他产品身份，
  也不把 `User-Agent` 当作当前稳定性根因

官方核对入口：

- [OpenAI Web search](https://developers.openai.com/api/docs/guides/tools-web-search)
- [OpenAI Streaming API responses](https://developers.openai.com/api/docs/guides/streaming-responses)
- [OpenAI Background mode](https://developers.openai.com/api/docs/guides/background)
- [OpenAI Cancel a response](https://developers.openai.com/api/reference/resources/responses/methods/cancel)
