---
status: verification_pending
implementation: local_implementation_complete
baseline_branch: main
baseline_head: 02e97e95db8dbb161fab0d33604fdc228f9b87c0
last_updated: 2026-08-12
---

# OpenAI 官方联网搜索插件化接入

## 1. 结论

把 OpenAI 官方 Web Search 作为独立工具提供给 DeepSeek、Gemini、本地模型和其他主聊天模型，
在 Kiyori 中技术上可行。

正式版本采用：

```text
任意主聊天模型
	└─ 调用 openai_web_search:search
		└─ 包管理 > 插件 > OpenAI Web Search
			└─ 可安装、可启停 ToolPkg 容器
				├─ 设置 UI
				├─ 可选输入菜单开关
				└─ openai_web_search subpackage
					└─ Kiyori 宿主 OpenAIHostedWebSearchGateway
						└─ OpenAI Responses 或严格兼容中转站
							└─ GPT-5.6 + hosted web_search
```

这个方案中的 GPT-5.6 不承担主聊天角色，只承担查询规划、网页搜索、网页阅读、证据整理和带来源
输出。但是从 OpenAI 协议与计费角度看，它仍然是一次模型请求，并不是一个脱离模型运行的普通
搜索 HTTP 接口。

第一版产品范围：

- 与 `APK 逆向工具包`、`楼层限制器`、`深度搜索`、`额外信息注入` 同级，作为 ToolPkg
  插件容器安装、启停和配置
- 通过 subpackage 暴露 `openai_web_search:search`
- 允许任何能使用 Kiyori 工具系统的主聊天模型调用
- 支持 OpenAI 官方 Responses endpoint
- 支持通过付费严格兼容探测的 Responses-compatible 中转站
- 第一阶段通用搜索模型允许列表为 `gpt-5.6`、`gpt-5.6-sol`、`gpt-5.6-terra`、
  `gpt-5.6-luna`
- 默认建议 `gpt-5.6-luna`，用户可以显式选择其他允许模型
- 官方当前还列出支持 Web Search 的专用 `gpt-5.6-cyber`；第一版不把安全领域专用模型纳入通用
  搜索 allowlist
- 中转站可以使用自定义模型别名，但不能只根据名称判断兼容
- 第一版默认执行 live 搜索，用户可以显式配置 indexed；不根据请求自动改变模式、模型、endpoint
  或搜索后端
- 返回答案、搜索动作、引用、完整来源、usage 和结构化错误
- 配置来源显式选择 `PACKAGE_ENV` 或 `MODEL_CONFIG`
- endpoint、模型、Key、认证、headers、思考强度和搜索参数可以由插件环境变量配置
- API Key 即使通过插件环境变量设置，也只允许宿主读取，ToolPkg JavaScript 不读取原始凭据

## 2. 用户想法的判断

| 想法 | 判断 | 说明 |
| --- | --- | --- |
| 与 `tavily.js` 并列，成为主模型可调用的独立搜索工具 | 工具关系正确，产品分类需修正 | 它们都能提供搜索工具，但本能力进入“插件”标签，不进入“脚本包”标签 |
| 与 APK 逆向、楼层限制器、深度搜索、额外信息注入同级 | 正确且是推荐形态 | 采用可安装、可启停 ToolPkg 容器 |
| OpenAI API 只用于搜索，不作为当前聊天模型 | 正确 | 产品角色可以完全分离 |
| 完全不经过 GPT，只调用 `web_search` | 不成立 | Responses `web_search` 由指定模型调用；Codex standalone search 请求也包含模型 |
| 单独设置 OpenAI 搜索 API Key | 可行且推荐 | 便于预算、限额、用量和吊销隔离 |
| 自动复用当前主模型 API Key | 不推荐 | 第三方主模型没有 OpenAI Key，切换主模型还会隐式改变费用归属 |
| 用户主动选择与既有 OpenAI Responses 配置共用 | 可支持 | `MODEL_CONFIG` 必须绑定固定配置，不复制 Key，不跟随当前聊天选择 |
| 通过环境变量自定义中转站、模型、Key 和思考强度 | 正确 | 使用 `PACKAGE_ENV`；Key 必须是包级敏感且仅宿主消费 |
| 纯 JavaScript 直连 OpenAI 作为正式版 | 可做原型，不是正式最佳方案 | 会暴露凭据给插件运行时，并形成第二套 Responses 协议实现 |
| `ToolPkg.registerAiProvider` | 不采用 | 它会把插件注册为完整主聊天供应商，与独立搜索工具的产品语义相反 |

## 3. 最佳方案

正式方案是“宿主协议内核 + ToolPkg 产品壳 + subpackage 工具”。

宿主持有：

- official 与 relay strict contract
- 包级 host-only 环境变量和模型配置绑定
- endpoint、认证、API Key 和额外请求头
- HTTP 请求、取消、超时、并发与速率限制
- Responses Web Search 请求编译
- `web_search_call`、annotations、sources 和 usage 解析
- revision `5` 的 evidence mode、source diagnostics：URL annotations、URL action sources、
  open-page actions、结构化 feed 和损坏条目
  分别建模
- 结构化错误分类
- 搜索证据、兼容成功记录和兼容失败记录的持久化及 UI 投影

ToolPkg 持有：

- 包管理中的安装、启停、版本和详情
- `openai_web_search:search` 工具定义
- 搜索服务配置页
- 配置来源、模型、live 模式、思考强度、上下文大小、域名和位置偏好
- 本地配置诊断和显式付费测试入口

ToolPkg 只提交结构化搜索请求并接收结构化结果。它不接触 API Key，不自己拼接
`Authorization` header，也不复制 Kiyori 已有的 OpenAI Responses 客户端。

环境变量只是 ToolPkg 插件的配置入口，不改变其产品分类。

## 4. 文档导航

- [官方合同与可行性](1_official_contract_and_feasibility.md)
- [插件与宿主架构](2_plugin_and_host_architecture.md)
- [请求、响应、凭据与安全](3_request_response_credentials_and_security.md)
- [实施、测试与验收](4_implementation_and_validation.md)
- [中转站与环境变量配置](5_relay_and_environment_configuration.md)
- [2026-08-12 日志深度分析](6_log_deep_analysis_20260812.md)
- [2026-08-12 后续修复计划](7_log_followup_fix_plan_20260812.md)
- [正式架构草案](../../doc-src/architecture/openai_hosted_web_search.md)

## 5. 已冻结的设计决策

- 产品类型是 ToolPkg 插件容器，不是普通 JS 脚本包
- container 默认关闭，subpackage 在容器启用后默认可用
- 配置来源显式二选一：`PACKAGE_ENV` 或 `MODEL_CONFIG`
- 两种配置来源不混合读取
- `PACKAGE_ENV` 支持中转站 endpoint、模型名、Key、认证、headers、reasoning 和搜索参数
- 原始 API Key 不进入现有全局 `EnvPreferences`、ToolPkg `privateData` 或 JavaScript 参数
- 正式版新增包级、敏感、`host_service` consumer 的 ToolPkg 环境变量语义
- 默认使用独立 OpenAI Project 或 Service Account Key
- 可以显式绑定一个已有的官方或经过严格探测的 Responses 模型配置
- 不自动读取当前聊天使用的 Provider、配置或模型
- 服务绑定使用稳定配置 ID 和精确模型名，不按模型列表索引静默选择其他模型
- official endpoint 使用 `RESPONSES_HOSTED_OFFICIAL`
- 中转站使用 `RESPONSES_RELAY_STRICT`，改变 endpoint、模型或认证后必须重新探测
- relay strict 的固定 `MODEL_CONFIG` 接受 `OPENAI_RESPONSES` 或
  `OPENAI_RESPONSES_GENERIC`；official 仍只接受官方 endpoint
- `tool_choice` 要求执行搜索；没有 `web_search_call` 时按协议错误处理
- `action.sources` 按集合解析；`oai-sports`、`oai-weather` 和 `oai-finance` 是已知无 URL
  实时 feed，不得因单个 feed 缺 URL 立即判定整次响应失败
- compatibility probe 固定访问 `developers.openai.com` 的公开网页并要求真实
  `url_citation`；不能再用会路由到时间、天气或金融实时 feed 的问题测试 URL 能力
- `url_citation` annotation 是行内 citation span 的权威证据；`action.sources` 是独立审计通道，
  不是 citation 白名单。只返回其中一种时保留真实证据；两者集合不一致时返回结构化差异诊断，
  不伪造另一种，也不伪造 citation span
- `source_diagnostics` 记录 response ID、action source coverage、citation/action/open-page URL、
  citation 差集、缺失 sources 的 search action 索引、无效来源数和有效 allowed domains
- response schema revision 为 `5`；旧 revision `4` 及更早成功或失败记录自动失效
- compatibility record-set 按 exact fingerprint digest 保存最多 `16` 条记录；同一 digest 的成功
  与失败互斥，不同 Key 的记录互不覆盖
- MODEL_CONFIG Key 选择与游标推进在 search 与 compatibility probe bridge 实例之间共享互斥
- 第一版使用非流式单次请求，`store=false`
- 第一版不使用 background response、sequence resume 或聊天历史
- 第一版不实现图片搜索、深度研究、多轮 search session、可由主模型继续调用的 `open` 或 `find`
- 第一版不接入 Codex `/v1/alpha/search`
- 搜索失败时不自动切换到 Tavily、浏览器抓取、Google Search 或其他服务
- 网页内容始终作为不可信证据，不能改变 Kiyori 指令、权限或工具边界

## 7. 2026-08-12 日志专项状态

指定日志的证据报告和后续修复计划已经写入：

- [日志深度分析](6_log_deep_analysis_20260812.md)
- [后续修复计划](7_log_followup_fix_plan_20260812.md)

本专项新增结论：

- Pixel relay 请求在 Android 客户端读取 HTTP response headers 前连接中止，提交状态保持未知
- 当前 at-most-once 状态机不应通过重复 POST、自动切换 endpoint 或静默改写请求来处理
- 日志中存在 API Key 前后缀、完整 prompt、重复完整堆栈和正常协程取消 ERROR
- UIHierarchyManager 绑定失败日志的状态语义和诊断字段需要单独修复
- 510ms 主线程警告和 4715ms TextSegmenter 预热先进入观测与验证阶段，不能仅凭这一条日志宣称
  首屏卡顿根因

该日志分析阶段的历史交付状态：

```text
ANALYSIS_DOCUMENTED
BUSINESS_SOURCE_UNCHANGED
RELAY_ROOT_CAUSE_PENDING
DEVICE_VERIFICATION_PENDING
NO_COMMIT
NO_PUSH
```

W1–W6 本地实现已经在当前 dirty worktree 中完成，专项状态更新为：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_PENDING
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
NO_COMMIT
NO_PUSH
```

本轮新增的本地证据包括：

- W1/W2 传输与隐私定向矩阵 `14/14`
- W3 错误所有权与传播定向测试 `10/10`
- W4 取消语义与关联流测试已通过
- W5 绑定状态机测试 `10/10`，并通过 `compileDebugKotlin`
- W6 ANR/TextSegmenter 观测、Activity 生命周期事实、W3/W5 综合定向矩阵通过
- `git diff --check` 无 whitespace error

W6 只增加观测字段，没有根据单条 `510ms` 或 `4715ms` 日志改变阈值、调度器、预热时机或
引入第二套分词器。

日志专项 W1–W6 的最终本地收尾证据如下：

- 定向 JVM 矩阵共 `51/51`，失败、错误和跳过均为 `0`
- `check_formal_readiness.py --require-main`：`Formal development readiness: PASS`
- `git diff --check`：无 whitespace error，仅有既有 CRLF 转换警告
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`，
  `238` 个任务中 `25` 个执行、`213` 个为最新状态；`verifySingleDebugLauncher` 与
  `verifyDebugPlayerRuntimePackaging` 通过
- 最终 Debug APK：
  `app/build/outputs/apk/debug/app-debug.apk`，观察时间 `2026-08-13 03:56:14 +08:00`，
  `472480557` bytes，SHA-256
  `D57921CB207114643FBBC6428E8ED3457930514FF264CB571A9A67239DD7192D`
- APK 为 `com.kiyori / 0.1.0 (45)`，min/target/compile SDK `26 / 34 / 37`，唯一
  `MainActivity` launcher，仅 `arm64-v8a`；51 个 `.so` 无重复 basename，另有
  `assets/operit_shell_exec`，共 52 个 AArch64 ELF，所有 `PT_LOAD >= 0x4000`
- Android Debug V2 单 signer 和 16 KB ZIP 对齐通过
- 内置 `openai_web_search.toolpkg` 为 `9864` bytes，SHA-256
  `558382BDDE9688F99395F703D3225C7DAB5452326F85A6660DDB557ACEA3B9FB`，与 APK 内条目逐字节一致
- APK 与 ToolPkg 敏感形状扫描为 `0`：Bearer credential、`sk-*`、长 API key assignment、私钥块、
  cookie credential

这组证据只关闭本地 W7 门禁。relay 连接中止根因、Pixel/OEM provider、启动性能时间线、
TextSegmenter 真实资源竞争和用户可见行为仍保持待验证，不能由本地构建结果替代。

## 6. 当前实施状态

revision `5` 已在 revision `4` 本地实现基础上完成：

- ToolPkg 容器级 `PACKAGE + HOST_SERVICE` 环境变量合同和 app-private 存储
- `PACKAGE_ENV` / `MODEL_CONFIG` 显式绑定解析
- official / relay strict policy、compatibility fingerprint 和付费探测入口
- 单次非流式 Responses Web Search gateway、取消、超时、限流和结构化错误
- 只序列化实际非空的 `allowed_domains` 或 `blocked_domains`，不把空数组发给 relay
- HTTP 非成功响应的有界、脱敏 provider error type/code/message 与 request/trace ID
- citation 与 action sources 的独立证据建模、coverage 和结构化差异诊断
- 多 Key compatibility record-set、仓库级原子合并与跨 bridge Key 选择串行化
- package-bound `ToolPkg.services.openAIWebSearch` bridge
- `com.kiyori.openai_web_search` ToolPkg、设置页和 `openai_web_search:search`
- 搜索结果 XML 安全承载、结构化 evidence parser 和可点击来源卡
- manifest、归档、bridge、policy、compiler、parser、gateway 和 UI evidence 的自动测试

用户先后在 APK 上执行了两次可能计费的真实中转站 compatibility probe。第一次暴露逐项 URL
fail-fast；第二次在修复该问题后得到 `CITATION_INVALID`。2026-08-12 的宿主外脱敏协议矩阵证明，
旧 probe 问“当前 UTC 日期”时中转返回命名 `api` feed，天然没有 URL citation。固定公开网页目标
时中转可以返回标准 `url_citation`；非流式完整 `action.sources` 是否返回还受
`x-openai-actor-authorization` 等中转扩展 header 影响；Codex 风格流式完成响应可只含
`url_citation`。因此第二次失败是 Kiyori 自定义 evidence 合同和 probe 目标共同造成，不是该中转
缺少 Web Search 能力。

revision `3` 自动矩阵为 `78/78`，但用户安装后在 Pixel 上得到 HTTP `502`，在 Sub2api 上得到
HTTP `401`。revision `3` APK SHA-256
`5767522CDEC73096B42A63E6CEA415A704E7F9AE56EA026F8FC17D468B1DF27C` 已经是现场失败基线，
不能继续称为修复版。

2026-08-12 使用用户明确授权的 Pixel 与 Sub2api endpoint、`gpt-5.6-sol` 和临时 Key 执行真实、
无重试、无自动切换的请求矩阵：

```text
普通 Responses
	Pixel: HTTP 200
	Sub2api: HTTP 200

OpenAI 官方最小 hosted web_search
	Pixel: HTTP 200，web_search_call + URL citation + action sources
	Sub2api: HTTP 200，web_search_call + URL citation + action sources

revision 3 精确请求
	Pixel: HTTP 502，provider type=upstream_error
	Sub2api: 客户端 90 秒超时

revision 3 精确请求，仅省略空 blocked_domains
	Pixel: HTTP 200，URL citation + action sources
	Sub2api: HTTP 200，URL citation + action sources
```

这组证据把 revision `3` 的共同根因锁定为 `OpenAIHostedWebSearchRequestCompiler` 在只有
`allowed_domains` 时仍发送空 `blocked_domains: []`。revision `4` 不改变非流式单请求方向，不
引入失败后重试、流式切换、字段剥离、actor header 注入或其他后端切换。Sub2api Android 现场的
`401` 与同一 Key 的桌面 HTTP `200` 不一致，因此 revision `4` 还必须显示脱敏 provider 错误详情、
request/trace ID、认证 scheme 和不可逆凭据修订指纹，供用户确认设备实际保存的绑定。

revision `4` APK 随后在 Sekirocloud 上成功完成 compatibility probe 和部分普通搜索，但用户新的
11 次现场矩阵为 `2` 次成功、`9` 次 `SOURCE_INVALID`。同一英文请求先成功后失败，有无
`allowed_domains`、中文或英文、`low` 或 `medium` 都不能稳定决定结果；Python、NASA 和 OpenAI
官方站点均可失败，F1 查询在返回完整 action sources 时成功。脱敏原始 Responses 对照得到：

```text
中文 OpenAI 限域查询
	HTTP 200
	web_search_call = 3
	citation URL = 2
	action source URL = 18
	1 个 citation URL 不在 action sources

英文 OpenAI 查询
	HTTP 200
	web_search_call = 3
	citation URL = 2
	action source URL = 0
	open_page URL = 1
```

旧 parser 只在 action-source URL 集合非空但未覆盖全部 citation 时抛错。这证明
`action.sources` 不是 citation annotation 的完整允许集合，也证明 `allowed_domains` 不是本轮
根因。revision `5` 保留 citation 的 HTTP(S)、span、source mapping 和 schema 硬校验，把跨通道
集合关系改为可观察诊断。

revision `5` 当前本地验证：

- 生产 Kotlin 编译已通过
- TypeScript strict 编译通过
- 完整定向矩阵 XML 精确为 `13` 个 suite、`91/91` tests，失败、错误和跳过均为 `0`
- formal readiness 与 `git diff --check` 通过
- architecture `phase=m03` 与架构门禁自身 `107/107` Python 单元测试通过
- revision `5` ToolPkg `1.0.4` 于 `2026-08-12 23:37:38 +08:00` 强制生成，为 `9864` bytes，
  SHA-256 `558382BDDE9688F99395F703D3225C7DAB5452326F85A6660DDB557ACEA3B9FB`
- ToolPkg 恰好包含 `5` 个运行时条目、`21` 个 host-service 环境变量和 `1` 个 subpackage；
  `.ts`、`.env`、三个实测 relay 域名、长 `sk-*`、Bearer credential 和私钥扫描均为 `0`
- revision `5` Debug APK 于 `2026-08-12 23:39:40 +08:00` 生成，为 `472480557` bytes，
  SHA-256 `9F845CC0F71CEBB1929E42148F93C85A489C9FAD20160FAE5E8BFF13EE451536`
- APK 为 `com.kiyori 0.1.0 (45)`，min/target/compile SDK `26 / 34 / 37`，唯一
  `MainActivity` launcher，仅 `arm64-v8a`；51 个 native 库无重复 basename，Android Debug V2
  单 signer 和 16 KB ZIP 对齐通过
- 51 个 `.so` 与 `assets/operit_shell_exec` 共 52 个 AArch64 ELF 的最小 `PT_LOAD` 对齐为
  `0x4000`
- APK 内 `openai_web_search.toolpkg` 恰好一份，与生成制品大小和 SHA-256 完全一致；APK 长
  `sk-*`、Bearer credential、AWS/GitHub token 和完整 PEM 私钥块扫描均为 `0`
- revision `4` ToolPkg `1.0.3` 为 `9864` bytes，SHA-256
  `B1A5AFFF5D16FD742A941845FB7EE7EC19B59142F98D5681285F440BE84B480B`，恰好 `5` 个运行时条目、
  `21` 个 host-service 环境变量和 `1` 个 subpackage；它已经是现场 `SOURCE_INVALID` 失败基线，
  不能继续作为修复版交付
- revision `3` ToolPkg 为 `9679` bytes，SHA-256
  `58025CC245D055D76345F45C9DE67E20D5464240F5033695B4C77380C1B94E24`，恰好 `5` 个运行时条目，
  manifest 含 `21` 个 host-service 环境变量，凭据特征扫描为 `0`
- revision `3` 产出构建 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 14s`，`238` 个任务中
  `30` 个执行、
  `208` 个为最新状态
- 文档回填和 ToolPkg 强制重生成后的一次串行复核为 `BUILD SUCCESSFUL in 25s`，`238` 个任务中
  `25` 个执行、`213` 个为最新状态；APK 时间、大小和 SHA-256 保持不变
- revision `3` Debug APK 生成于 `2026-08-12 19:35:10 +08:00`，为 `472480557` bytes，SHA-256
  `5767522CDEC73096B42A63E6CEA415A704E7F9AE56EA026F8FC17D468B1DF27C`
- revision `3` APK 为 `com.kiyori 0.1.0 (45)`，min/target/compile SDK `26 / 34 / 37`，唯一
  `MainActivity` launcher，仅 `arm64-v8a`，51 个 native 库无重复 basename
- Android Debug 仅 V2、单 signer，证书 SHA-256
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`；16 KB ZIP 对齐通过
- APK 内 `openai_web_search.toolpkg` 恰好一份，为 `9679` bytes、SHA-256
  `58025CC245D055D76345F45C9DE67E20D5464240F5033695B4C77380C1B94E24`，与生成制品逐字节相同
- ToolPkg 与 APK 解压后共扫描 `5611` 个归档条目；用户凭据精确值、中转域名、长 `sk-*` 值和
  Bearer credential value 均为 `0`

精确验证矩阵与产物审计见
[实施、测试与验收](4_implementation_and_validation.md)。
