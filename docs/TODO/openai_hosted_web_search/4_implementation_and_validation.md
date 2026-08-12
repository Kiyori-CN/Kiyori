# 4. 实施、测试与验收

## 4.1 授权边界

当前状态：

```text
LOCAL IMPLEMENTATION COMPLETE
REVISION 5 LOCAL VERIFICATION COMPLETE
FINAL LOCAL ARTIFACT VERIFICATION COMPLETE
GIT DELIVERY AUTHORIZED
DEVICE VERIFICATION PENDING
```

用户已经明确下达开发指令，并在当前轮次授权提交和推送。revision `5` 业务代码、完整定向矩阵、
ToolPkg `1.0.4`、正式开发门禁、Debug APK 构建和静态制品封板均已完成；Git 交付以本轮最终
提交、推送和远端 ref 对账结果为准。

当前仍未获授权并且没有执行：

- 安装 APK
- 执行 ADB、MuMu 或真机操作

用户已在修复前 APK 上显式执行过多轮真实中转站 probe 和普通搜索。当前任务还在用户明确授权下
完成了脱敏宿主外协议对照；真实凭据没有写入源码、文档、测试、ToolPkg、APK 或任务日记。
revision `5` APK 已生成并完成本地静态审计，但尚未安装和重新探测，因此当前产品验收状态是
`verification_pending`，不是已经完成真实服务与设备验收。

## 4.2 实施里程碑

### M0：ToolPkg 容器环境变量基础 `[LOCAL DONE]`

目标：

- 给 ToolPkg manifest 增加容器级环境变量声明
- 增加 package scope
- 增加 sensitive 和 password UI
- 增加 `host_service` consumer
- 建立 package-bound host environment repository
- 保留普通脚本包现有全局环境变量合同

实际主要文件：

```text
app/src/main/java/com/ai/assistance/operit/core/tools/ToolPackage.kt
app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageManager.kt
app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgParser.kt
app/src/main/java/com/ai/assistance/operit/data/preferences/ToolPkgHostEnvironmentRepository.kt
app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/PackageEnvironmentVariablesPolicy.kt
app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/PackageEnvironmentVariablesSheet.kt
app/src/main/java/com/ai/assistance/operit/ui/features/packages/screens/PackageManagerScreen.kt
```

本地验收：

- container manifest 可以声明环境变量
- Key 使用 package scope、sensitive、host_service 和 password
- ToolPkg JavaScript 的 `getEnv()` 无法读取 host-only Key
- 另一个 ToolPkg 无法读取目标 package namespace
- 包管理环境变量界面可以编辑、遮蔽和清除 Key
- artifact scanner 拒绝非法 consumer、scope 和 input type
- 旧脚本包 env 行为保持不变

上述合同已经由 `PackageEnvironmentVariablesPolicyTest`、`ToolPkgArtifactScannerTest` 和
`OpenAIWebSearchToolPkgContractTest` 覆盖。

### M1：能力与绑定模型 `[LOCAL DONE]`

目标：

- 定义 hosted web search 强类型模型
- 定义 `PACKAGE_ENV` 与 `MODEL_CONFIG`
- 定义 `RESPONSES_HOSTED_OFFICIAL` 与 `RESPONSES_RELAY_STRICT`
- 定义固定服务绑定和兼容探测指纹
- 复用 official endpoint 判定
- 增加 official GPT-5.6 allowlist

实际主要文件：

```text
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/
	OpenAIHostedWebSearchModels.kt
	OpenAIHostedWebSearchPolicy.kt
	OpenAIHostedWebSearchBindingResolver.kt
	OpenAIHostedWebSearchCompatibilityRepository.kt
	OpenAIHostedWebSearchCompatibilityProbe.kt
```

验收：

- 配置来源缺失或混用时被拒绝
- official endpoint 进入 official contract
- 中转 endpoint 未探测时被拒绝
- 中转探测必须返回 web search call 与真实 URL citation；action sources 是独立审计通道
- endpoint、模型、认证或 reasoning 改变后探测失效
- Chat Completions provider 被拒绝
- official 模型不存在或不在 allowlist 时被拒绝
- `gpt-5.6-cyber` 在第一版通用搜索模式中被明确拒绝
- 模型列表重排不改变绑定模型
- Key 不进入 ToolPkg

没有新增虚构的单一 `OpenAIHostedWebSearchBindingRepository`。绑定状态继续由
`ToolPkgHostEnvironmentRepository`、`ModelConfigManager` 和
`OpenAIHostedWebSearchCompatibilityRepository` 分别持有，再由
`OpenAIHostedWebSearchBindingResolver` 编译为一次调用所需的不可变绑定。

### M2：请求编译和响应解析 `[LOCAL DONE]`

目标：

- 建立搜索专用 Responses compiler/parser
- 复用 `SharedHttpClient`、ModelConfig Key pool 数据和 usage 解析
- 支持 package env 与固定 model config 的传输身份
- 支持 configurable reasoning、headers 和中转 endpoint
- 完成 source marker 转换

建议影响文件：

```text
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/
	OpenAIHostedWebSearchGateway.kt
	OpenAIHostedWebSearchRequestCompiler.kt
	OpenAIHostedWebSearchResponseParser.kt
	OpenAIHostedWebSearchHttpFailurePolicy.kt
```

验收：

- 请求固定 `tool_choice=required`
- 请求显式 `external_web_access=true`
- 请求 `store=false`
- 请求包含完整 sources
- `return_token_budget` 只接受 `default` 或 `unlimited`
- reasoning `omit` 时不发送字段，其他值发送精确 effort
- extra headers 不能覆盖认证或 hop-by-hop header
- redirect 被拒绝
- parser 保留 action、citation、source 和 usage
- parser 遍历全部 output 和全部 web_search_call，并保留 source diagnostics
- citation/action source URL 集合不一致时保留有效 citation，不再抛出 `SOURCE_INVALID`
- 没有 `web_search_call` 时失败
- citation index 越界时失败或产生明确 warning
- 日志不包含 Key

Web Search 不直接调用通用 `ApiKeyProvider`。专用 Key 选择器只读取已经选择的配置来源，不记录
Key 前后缀；多 Key 模式没有可用成员时返回 `API_KEY_MISSING`，也不读取其他 Key 来源。

### M3：ToolPkg 宿主桥 `[LOCAL DONE]`

目标：

- 提供 package-bound bridge
- 绑定 caller identity
- 增加取消、状态和本地校验
- 提供显式付费 compatibility probe

实际主要文件：

```text
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/
	ToolPkgOpenAIWebSearchBridge.kt

app/src/main/java/com/ai/assistance/operit/core/tools/javascript/
	JsEngine.kt
	JsToolPkgRegistration.kt

examples/types/
	toolpkg.d.ts
```

验收：

- 普通脚本不能冒充目标 ToolPkg
- ToolPkg 不能传 package ID 访问其他包绑定
- ToolPkg 不能传 endpoint、Key 或 Authorization
- ToolPkg 不能读取 host-only environment
- 调用取消能结束对应 HTTP call
- 未绑定状态返回结构化错误

bridge 只为绑定到 `com.kiyori.openai_web_search` 的执行引擎初始化，并要求 `callId` 属于当前
`JsEngine.activeExecutionSessions`。compatibility probe 还要求当前 execution runtime 为 `ui`；
subpackage 和 sandbox runtime 仍可调用普通 `search`。

### M4：ToolPkg 产品壳 `[LOCAL DONE]`

目标：

- 创建 `com.kiyori.openai_web_search`
- 增加设置 UI
- 增加 subpackage 搜索工具
- 加入包管理安装和启停
- 环境变量配置进入 ToolPkg 容器，不进入脚本包

建议目录：

```text
examples/openai_web_search/
	manifest.json
	src/
	dist/
```

当前使用既有 bundled ToolPkg 生成任务，不手工把归档复制进源码资产目录。生成结果位于：

```text
app/build/generated/bundledToolPkgAssets/packages/openai_web_search.toolpkg
```

验收：

- manifest 与 distribution allowlist 通过
- 工具名稳定为 `openai_web_search:search`
- container 默认关闭
- subpackage 在 container 启用后默认开启
- API Key 声明为 package-scoped sensitive host-only environment
- 设置页不显示或返回原始 Key
- 纯本地配置校验不产生 OpenAI 费用
- compatibility probe 有明确付费确认

### M5：证据持久化和 UI `[LOCAL DONE]`

目标：

- 工具调用保留结构化 evidence
- 聊天 UI 显示搜索证据卡
- 来源可点击

实际主要文件：

```text
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/
	OpenAIHostedWebSearchEvidence.kt
	OpenAIHostedWebSearchToolResultMarkupCodec.kt

app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/part/
	OpenAIWebSearchToolResultDisplay.kt
	CustomXmlRenderer.kt
```

当前复用 provider tool invocation ledger 作为持久 owner，不增加第二份 sidecar。完整结构化 JSON
同时保留在工具结果与 ledger 中；聊天 XML 边界只对目标 ToolPkg 的 JSON 编码 `<`、`>`、`&`，
避免网页文本注入 `</content>` 或 `</tool_result>`，JSON 解码后仍恢复原 answer、URL 和 citation
offset。

验收：

- 主模型改写答案后，原搜索证据仍可查看
- citation span 只作用于原 search answer
- source URL 可点击
- 同一工具调用的 source ID 稳定
- 删除聊天时清理关联 evidence
- 导出与备份策略明确

本轮专用来源卡已经实现并验证前四项。聊天删除、导出和备份继续复用现有 tool invocation ledger
生命周期，不引入新的独立 evidence 数据库。

### M6：原生 OpenAI Responses 模式 `[DEFERRED]`

后续可选，不进入第一版完成标准。

目标：

- 当前主模型为官方 OpenAI Responses 时直接注入 `web_search`
- 保留原生 message annotations
- 避免第二个搜索模型请求

门禁：

- 用户显式选择
- capability profile 明确支持
- 与独立工具模式具有不同配置和状态
- 不在运行失败时切换模式

### M7：Codex standalone backend `[DEFERRED]`

后续实验，不进入当前路线。

只有以下条件全部满足才开始：

- OpenAI 发布公开稳定 API 文档
- endpoint 不再只处于 alpha
- API Key 与认证合同明确
- 模型兼容和计费明确
- response/history/ref_id 具有版本合同
- Android 客户端可以完成 mock 与真实验收

## 4.3 建议测试矩阵

### Request compiler

- GPT-5.6 四模型
- relay 自定义模型别名
- official 与 relay strict
- package env 与 model config
- live/indexed mode
- low/medium/high
- return token budget default/unlimited
- reasoning omit/none/low/medium/high/xhigh/max
- max output tokens
- allowed domains
- blocked domains
- approximate location
- `include` 合并去重
- `store=false`
- `stream=false`
- `tool_choice=required`
- 禁止 caller 覆盖 endpoint/model/key
- extra headers 冲突
- redirect reject

### Response parser

- 单次 search
- 多个 search action
- open_page
- find_in_page
- 多个 message output item
- 多个 output_text part
- url citation
- citation 重叠
- citation index 越界
- 完整 sources
- URL source 与 `oai-sports` / `oai-weather` / `oai-finance` 实时 feed 混合集合
- relay 命名 `api` feed
- citation-only response
- action-sources-only response
- structured-feed-only response
- text-only response 明确失败
- 单个缺 URL、未知 type、非对象或非法 URL source 的集合级诊断
- citation 与 action sources 同时存在但 URL 不对齐时成功，并报告精确差集
- action source coverage 分别覆盖 missing、partial、complete 与 not applicable
- `open_page.url` 进入动作诊断但不冒充 citation 或 action source
- citation-only source 只来自真实 annotation，不声称 action source 存在
- 没有真实 citation span 时不生成 `[S1]`
- source 缺 title
- source 非 HTTP URL
- source 去重
- usage
- 无 web_search_call
- 无文本
- failed/incomplete response

### Binding policy

- binding 缺失
- config source 缺失
- config source 混用
- package env 缺失
- config 缺失
- config 删除
- model 从配置列表删除
- model 列表重排
- 官方 endpoint
- 未探测的 OpenAI-compatible endpoint
- 探测通过的 Responses-compatible endpoint
- endpoint/model/auth/reasoning 变化导致探测失效
- Chat Completions endpoint
- 空 Key
- 单 Key
- Key pool
- 禁用 Key

### Security

- ToolPkg 不能读取绑定 Key
- ToolPkg 不能读取 package host-only Key
- 其他 ToolPkg 不能读取该 package namespace
- bridge 不接受 ToolPkg ID 参数
- 非目标 ToolPkg 调用被拒绝
- APK、资源、ToolPkg 和 fixture 不包含 Kiyori 自有共享 Key
- 个人 BYOK 直连与服务端代理合同不混用
- 日志无 Authorization
- 日志无 Key 前后缀
- response/error 脱敏
- password 输入不回显
- 环境变量导出不包含敏感值
- 网页 prompt injection fixture
- domain validation
- location 默认关闭

### ToolPkg

- manifest parser
- container-level environment parser
- subpackage 工具注册
- 包启停
- 设置路由
- process rebuild
- privateData 只保存非秘密 UI 状态
- `.toolpkg` scanner/builder
- package update 后 binding 保留
- plugin 出现在“插件”而不是“脚本包”

### Chat integration

- DeepSeek 主模型调用
- Gemini 主模型调用
- 本地模型使用 XML 工具调用
- OpenAI Chat Completions 主模型调用
- OpenAI Responses 主模型调用独立工具
- 主模型切换不改变 search binding
- 工具失败不会被报告为成功
- evidence card 与 tool invocation 对齐

## 4.4 自动测试不得调用真实 OpenAI

自动测试使用：

- `MockWebServer`
- 固定 JSON fixtures
- 伪造 SSE fixture 仅在未来引入 streaming 时使用
- 固定 HTTP 401、403、429、5xx
- 延迟和取消 fixture

真实 OpenAI 验收只能作为用户确认后的手工 smoke test，并单独记录：

- 时间
- model
- query
- response ID
- usage
- web search call count
- 费用提示
- 结果与 citation

不得把 API Key、完整 header 或账号信息写入测试报告。

## 4.5 推荐第一版验收场景

### 场景 A：第三方主模型

```text
主模型：DeepSeek
搜索服务：OpenAI Responses / gpt-5.6-luna
问题：查询一个当天可能变化的公开事实
```

通过条件：

- DeepSeek 调用 `openai_web_search:search`
- OpenAI 搜索绑定不受 DeepSeek 配置影响
- 工具返回 answer、citations、sources 和 usage
- UI 显示独立证据卡

### 场景 B：显式共用配置

```text
主模型：任意
搜索服务：用户选定的既有官方 OpenAI Responses config
```

通过条件：

- 插件不复制 Key
- 配置页面明确显示共用关系
- 删除该 config 后插件显示失效，不选择其他 config

### 场景 C：独立搜索项目

```text
主模型：任意
搜索服务：PACKAGE_ENV
endpoint：官方 OpenAI 或严格兼容中转站
Key：独立 Key
```

通过条件：

- 搜索用量可与主聊天用量区分
- 搜索 Key 可单独吊销
- 主模型切换不影响搜索

### 场景 D：中转站

```text
配置来源：PACKAGE_ENV
endpoint：用户中转站 Responses URL
model：用户填写的 GPT-5.6 名称或中转别名
reasoning：用户选择的精确值
```

通过条件：

- 兼容探测前工具不可执行
- 探测固定访问公开网页，并实际返回 `web_search_call` 与 URL citation
- 探测通过后可以被第三方主模型调用
- 改变 endpoint、模型、认证或 reasoning 后要求重新探测
- 只返回普通文本时明确判定为不兼容
- sources 混有官方实时 feed 或 relay 命名 `api` feed 时不因 feed 无 URL fail-fast
- citation 与 action sources 同时出现但不完全覆盖时保留有效 citation并返回结构化诊断
- citation-only、action-sources-only 与 structured-feed-only 普通搜索分别保留真实证据
- 失败状态记录错误码、可选 HTTP 状态和脱敏消息

### 场景 E：配置错误

通过条件：

- 兼容 endpoint、错误模型、空 Key 和缺失 binding 均返回确定错误
- 不发起其他搜索请求
- 不返回模型记忆答案

### 场景 F：取消

通过条件：

- 用户停止后取消当前 HTTP call
- 工具状态为 cancelled
- 不产生第二个请求
- 后续新调用不继承旧错误

## 4.6 构建与验证顺序

本轮实际按以下顺序执行：

1. 定向 JVM 单元测试
2. ToolPkg TypeScript 编译
3. bundled ToolPkg scanner/builder
4. Markdown 链接检查
5. formal readiness
6. `git diff --check`
7. 相关架构门禁
8. `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
9. 核验 Debug APK

真实 OpenAI smoke 和目标设备验收与本地构建证据分开报告。

## 4.7 第一版完成标准

- [x] 任意支持 Kiyori 工具调用的主聊天模型都能看到独立 OpenAI Web Search 工具合同
- [x] 插件显示在包管理“插件”标签
- [x] container 可以独立安装、更新、启停
- [x] 搜索绑定不跟随当前聊天 Provider
- [x] API Key 不进入 ToolPkg JavaScript
- [x] `PACKAGE_ENV` 和 `MODEL_CONFIG` 显式二选一
- [x] package env 支持 endpoint、model、Key、auth、headers 和 reasoning
- [x] Key 是 package-scoped sensitive host-only environment
- [x] APK、ToolPkg 和源码不内置 Kiyori 自有共享 Key
- [x] official Responses contract 生效
- [x] relay strict compatibility probe 生效
- [x] 中转站只返回普通文本时不能标记兼容
- [x] GPT-5.6 allowlist 生效
- [x] 请求强制产生 Web Search call
- [x] live mode 显式启用
- [x] indexed 只能由用户配置显式启用，模型调用参数不能临时切换
- [x] answer、action、citation、source、evidence mode 和 usage 无损解析
- [x] 搜索 evidence 可持久查看
- [x] 配置错误和 HTTP 错误结构化可见
- [x] 没有自动搜索服务切换
- [x] 自动测试不调用付费 API
- [x] 定向测试、正式门禁和 Debug APK 构建通过
- [x] 真实 API smoke test 单独获授权并以脱敏结构记录
- [ ] 目标设备包管理、设置页、工具调用和证据卡验收完成

本地实现、自动化合同与真实中转协议矩阵已经完成，不代表目标设备和用户现场验收已经完成。

## 4.8 本地自动验证结果

### TypeScript 与 ToolPkg

- revision `5` TypeScript strict 编译通过
- revision `5` manifest 为 `com.kiyori.openai_web_search` `1.0.4`，包含 `21` 个
  package-scoped host-service 环境变量和 `1` 个 subpackage
- revision `5` `:app:generateBundledToolPkgAssets --rerun-tasks --no-daemon --console=plain`
  为 `BUILD SUCCESSFUL in 12s`
- revision `5` ToolPkg 生成于 `2026-08-12 23:37:38 +08:00`，大小 `9864` bytes，SHA-256
  `558382BDDE9688F99395F703D3225C7DAB5452326F85A6660DDB557ACEA3B9FB`
- revision `5` 归档恰好包含 `5` 个运行时条目；`.ts`、`.env`、三个实测 relay 域名、长
  `sk-*`、Bearer credential 和私钥扫描均为 `0`
- revision `4` ToolPkg 历史大小：`9864` bytes
- revision `4` ToolPkg 历史 SHA-256：
  `B1A5AFFF5D16FD742A941845FB7EE7EC19B59142F98D5681285F440BE84B480B`
- revision `4` ToolPkg 历史归档条目数：`5`；它已经被用户现场证明存在 `SOURCE_INVALID`，
  不能作为 revision `5` 修复制品
- revision `3` ToolPkg 大小 `9679` bytes、SHA-256
  `58025CC245D055D76345F45C9DE67E20D5464240F5033695B4C77380C1B94E24`，已被用户现场证明失败
- 修复前 ToolPkg 生成时间早于 sources 集合修复，大小 `8345` bytes、SHA-256
  `0BECAEF4C4E501EEF8D9F43F439339ADC82C0952F92D931CB9349989C7AABD9F`，已过期
- 归档只包含：

```text
dist/main.js
dist/packages/openai_web_search.js
dist/shared.js
dist/ui/index.ui.js
manifest.json
```

归档不包含 `.env`、TypeScript 源码、API Key 或其他凭据。

### 定向 JVM 测试

revision `5` 完整定向矩阵使用 `--rerun-tasks` 强制执行后，测试 XML 于
`2026-08-12 23:36:43 +08:00` 精确汇总为
`13` 个 suite、`91/91`
tests，失败 `0`、错误 `0`、跳过 `0`：

```text
OpenAIHostedWebSearchBindingCompilerTest             5
OpenAIHostedWebSearchCompatibilityProbePolicyTest    5
OpenAIHostedWebSearchCompatibilityRecordSetTest      6
OpenAIHostedWebSearchEvidenceParserTest              7
OpenAIHostedWebSearchGatewayTest                     6
OpenAIHostedWebSearchHttpFailurePolicyTest           3
OpenAIHostedWebSearchPolicyTest                      5
OpenAIHostedWebSearchRequestCompilerTest             3
OpenAIHostedWebSearchResponseParserTest             17
ToolPkgOpenAIWebSearchBridgePolicyTest              13
OpenAIWebSearchToolPkgContractTest                   5
ToolPkgArtifactScannerTest                           7
PackageEnvironmentVariablesPolicyTest                9
```

这些自动测试全部使用本地 fixture 或 mock transport，没有调用真实 OpenAI 或中转站。revision
`4` 的 `12 / 82` 只作为历史结果保留，不能代替当前 schema revision `5` 的矩阵。

### 真实中转协议矩阵

用户明确授权后，于 2026-08-12 使用与当前 Codex 相同的 endpoint、模型和 Key 做最小真实请求。
输出只保留 HTTP 状态、字段、事件类型、数量和 URL host，不保存答案全文、Key 或鉴权 header 值。

```text
旧 UTC 日期 probe
	HTTP 200
	web_search_call + message
	source = {type:"api", name:...}
	url_citation = 0

固定 developers.openai.com 页面，非流式
	HTTP 200
	url_citation = 1
	action.sources = 0

同一页面，非流式 + x-openai-actor-authorization=enabled
	HTTP 200
	url_citation = 1
	action.sources = URL entries

同一页面，流式 + actor header
	HTTP 200
	response.output_text.annotation.added = 1
	completed response 的 action.sources 仍可为空
```

第一轮结论：中转支持 Web Search；旧 probe 目标和 Kiyori 的双通道强制门禁是 revision `2`
失败根因。

revision `3` 现场失败后的第二轮矩阵：

```text
普通 Responses
	Pixel: HTTP 200
	Sub2api: HTTP 200

OpenAI 官方最小 hosted web_search
	Pixel: HTTP 200，web_search_call + URL citation + action sources
	Sub2api: HTTP 200，web_search_call + URL citation + action sources

revision 3 精确请求
	Pixel: HTTP 502，provider type=upstream_error，message=Upstream request failed
	Sub2api: 90 秒客户端超时

revision 3 精确请求，仅省略空 blocked_domains
	Pixel: HTTP 200，URL citation=1，action sources=25
	Sub2api: HTTP 200，URL citation=1，action sources=25
```

第二轮结论：共同根因是只配置 `allowed_domains` 时仍发送
`blocked_domains: []`。actor header、流式请求、模型切换、Key 切换和后端切换都不是本轮修复。

### 项目门禁

- 当前 TypeScript、ToolPkg 生成、`git diff --check` 和
  `ci/script/check_formal_readiness.py --repository . --require-main`：`PASS`
- `ci/script/check_architecture_boundaries.py --repository . --phase m03` 使用当前新增的
  `OpenAIHostedWebSearchCompatibilityRepository` 与 `ToolPkgHostEnvironmentRepository`
  持久化调用合同快照：`PASS`；两条 `getSharedPreferences` 调用已登记，不改变既有持久化
  owner 或数据迁移边界
- 架构门禁自身 Python 单元测试：`107/107`，失败和错误均为 `0`
- 既有 fresh-clone 结果只证明已提交基线
  `02e97e95db8dbb161fab0d33604fdc228f9b87c0` 可从新克隆复现，不包含本次未提交实现

### Debug APK

- revision `5` 构建命令：`.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
- revision `5` 构建结果：`BUILD SUCCESSFUL in 39s`
- Gradle 任务：`238 actionable tasks: 28 executed, 210 up-to-date`
- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 生成时间：`2026-08-12 23:39:40 +08:00`
- 大小：`472480557` bytes
- SHA-256：`9F845CC0F71CEBB1929E42148F93C85A489C9FAD20160FAE5E8BFF13EE451536`
- application ID：`com.kiyori`
- version：`0.1.0 (45)`
- minSdk / targetSdk / compileSdk：`26 / 34 / 37`
- launcher：唯一 `com.ai.assistance.operit.ui.main.MainActivity`
- native ABI：仅 `arm64-v8a`
- native libraries：`51` 个 `.so`，无重复 basename
- `51` 个 `.so` 加 `assets/operit_shell_exec` 共 `52` 个 ELF 均为 AArch64；`160` 个
  `PT_LOAD` segment 的最小对齐为 `0x4000`
- 签名：Android Debug，仅 APK Signature Scheme V2，单 signer
- signer certificate SHA-256：
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`
- `zipalign -c -P 16 -v 4`：`Verification successful`
- APK 内 `assets/packages/openai_web_search.toolpkg` 恰好一份，为 `9864` bytes、SHA-256
  `558382BDDE9688F99395F703D3225C7DAB5452326F85A6660DDB557ACEA3B9FB`，与生成目录产物逐字节相同
- APK 共 `5635` 个文件条目；长 `sk-*`、Bearer credential、AWS/GitHub token 和完整 PEM
  私钥块扫描均为 `0`
- APK 内可见的私钥头字面量来自 Kiyori ToolPkg 安全拒绝规则和依赖二进制固定文本，不存在成对
  的 PEM 私钥正文

历史失败基线：

- 修复前 APK 生成于 `2026-08-12 15:39:23 +08:00`，大小 `472480077` bytes、SHA-256
  `428678C582ADFE2096A98F713FCA976D33D6040F78731E61871325B1251C367B`
- SHA-256 `3152BC745C89F914F1703A35189E5B2137ACE633AE465BBA066811B2022F541C` 的上一轮 APK 已被用户
  现场证明仍会报 `CITATION_INVALID`，是 revision `2` 失败基线，不能用于重新验证
- revision `3` 产出构建结果：`BUILD SUCCESSFUL in 1m 14s`
- Gradle 任务：`238 actionable tasks: 30 executed, 208 up-to-date`
- 文档回填和 ToolPkg 强制重生成后的一次串行复核：`BUILD SUCCESSFUL in 25s`，
  `238 actionable tasks: 25 executed, 213 up-to-date`；APK 时间、大小和 SHA-256 保持不变
- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 生成时间：`2026-08-12 19:35:10 +08:00`
- 大小：`472480557` bytes
- SHA-256：`5767522CDEC73096B42A63E6CEA415A704E7F9AE56EA026F8FC17D468B1DF27C`
- application ID：`com.kiyori`
- version：`0.1.0 (45)`
- minSdk：`26`
- targetSdk：`34`
- compileSdk：`37`
- launcher：唯一 `com.ai.assistance.operit.ui.main.MainActivity`
- native ABI：仅 `arm64-v8a`
- native libraries：`51` 个 `.so`，无重复 basename
- 签名：Android Debug，仅 APK Signature Scheme V2，单 signer
- signer certificate SHA-256：
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`
- `zipalign -c -P 16 -v 4`：`Verification successful`
- APK 内 `assets/packages/openai_web_search.toolpkg` 恰好一份
- APK 内 ToolPkg 为 `9679` bytes，SHA-256
  `58025CC245D055D76345F45C9DE67E20D5464240F5033695B4C77380C1B94E24`，与生成目录产物逐字节相同
- ToolPkg 与 APK 解压后共扫描 `5611` 个归档条目；用户凭据精确值、中转域名、长 `sk-*` 值和
  Bearer credential value 均为 `0`

## 4.9 待验证与最短下一步

宿主外真实中转协议 smoke 已获授权并完成。仍需用户在 Android 现场完成：

1. 安装 revision `5` APK，对目标 relay 绑定分别重新保存环境变量，并记录状态页的
   `API key revision`、`Auth scheme kind` 后执行一次可能计费的 compatibility probe
2. 探测通过后执行一条真实 `openai_web_search:search`，核对 response ID、usage、Web Search call、
   `evidence_mode`、citations、sources 与 `source_diagnostics`；确认 citation-only、partial
   action sources 与 complete action sources 都不再触发旧 `SOURCE_INVALID`
3. 在目标设备验收包管理开关、环境变量遮蔽、设置页、第三方主模型工具调用、取消和来源卡

这些验证不能由 mock、JVM 测试、宿主外协议 smoke 或 APK 静态审计替代。完成现场复测前，任务
状态保持 `verification_pending`。
