---
status: implementation_complete
implementation: revision_7_local_complete
source_changes: local_code_and_documentation
log_date: 2026-08-13
log_timezone: Asia/Shanghai
log_sha256: BB2F0124ADD4856CD84DEC34A6337EEA743FC48A8F2C4F23607F9D7CBA3396FF
field_test_calls: 29
version_evidence: probable_revision_6
---

# 2026-08-13 现场矩阵深度分析与 revision 7 实施计划

## 1. 任务边界与最终结论

本报告分析以下最新日志和用户提供的现场测试摘要：

```text
kiyori_log_20260813_193725.txt
```

本轮只执行日志读取、源码与测试合同审计、架构设计和文档写入。没有修改 Kotlin、TypeScript、
ToolPkg、资源、测试实现或构建逻辑；没有调用真实 OpenAI、relay 或其他计费接口，没有安装 APK，
没有操作真机、模拟器、ADB 或 MuMu，也没有提交或推送。

现场矩阵与日志可以相互对齐：

- 日志中出现四批 `5 + 8 + 8 + 8` 个 `openai_web_search:search`，合计恰好 `29` 次顶层调用
- 用户报告 `23` 次结构化成功、`5` 次预期参数拒绝和 `1` 次非预期零证据失败
- 日志中的六个失败终态与该分布一致
- 第三批包含一个 `RELAY_RESPONSE_TEXT_ONLY` 和四个预期参数错误
- 第四批包含一个预期的完整 URL 域名拒绝

revision `6` 的本地生命周期、admission、唯一 `PACKAGE_ENV`、结果卡和 evidence parser 实现并未在
这次现场测试中复发旧的超时误分类或取消竞态问题。现场暴露的是下一层生产问题：

1. 域名过滤被当作请求提示发送，但客户端没有验证 relay 是否遵守，`blocked_domains` 因而不构成
   可声明的安全边界
2. URL 处理使用错误的 URI 重建方式，能够确定地产生百分号双重编码；来源身份又直接使用完整 URL
   字符串，造成 tracking query 差异被误报为跨通道缺失
3. 无 URL citation、action source 或 structured feed 的结果被无条件提升为硬失败，合法零结果
   无法结构化返回
4. provider 返回的空 Markdown 链接没有在用户可见答案前清理
5. ToolPkg 公开参数把原生 array 和 boolean 伪装成 string，制造二次 JSON 和裸 QuickJS
   `SyntaxError`
6. 成功响应没有请求总耗时、阶段耗时、位置应用结果和域名策略执行证据
7. 同一个 ToolPkg 失败被三层完整记录，六次失败扩张为十八条 ERROR
8. 之前的日志隐私修复没有覆盖模型设置保存和语音/自动朗读 preview 日志

当前状态应更新为：

```text
REVISION_6_LOCAL_IMPLEMENTATION_COMPLETE
REVISION_6_FIELD_MATRIX_EXECUTED
FIELD_FUNCTIONAL_ACCEPTANCE_FAILED
REVISION_7_ANALYSIS_COMPLETE
REVISION_7_IMPLEMENTATION_PENDING
DEVICE_UI_PENDING
NO_COMMIT
NO_PUSH
```

## 2. 日志不可变基线

| 项目 | 已确认值 |
| --- | --- |
| 文件名 | `kiyori_log_20260813_193725.txt` |
| 文件大小 | `170628` bytes |
| SHA-256 | `BB2F0124ADD4856CD84DEC34A6337EEA743FC48A8F2C4F23607F9D7CBA3396FF` |
| 编码 | UTF-8，无解码错误 |
| 换行 | LF |
| NUL | `0` |
| 物理行数 | `1387` |
| 日志头声明条数 | `1382` |
| 带时间戳行数 | `1244` |
| 时间范围 | 2026-08-13 19:18:41.706 至 19:37:20.869 |
| 覆盖时长 | 18 分 39.163 秒 |
| 级别分布 | DEBUG `1014`、INFO `202`、WARN `10`、ERROR `18` |
| 完全重复的额外行 | `123` |
| 唯一物理行比例 | `0.9113` |

日志头声明条数不是物理行数。多行堆栈、文件头和空行会造成两者差异，本次没有证据说明日志正文
被截断。末尾存在正常的会话完成和后续状态事件。

隐私扫描结果：

- 没有完整 OpenAI Key、Bearer credential、Authorization、Cookie、私钥块或完整请求正文
- 没有 Web Search query、answer、sources 或完整 result JSON 的专用 parser-invalid 泄漏
- 仍有四条模型设置保存日志记录 API Key 前缀片段和完整自定义 endpoint
- 仍有两条会话完成日志记录用户可见回复的 50 字符 preview

本报告不保存上述 Key 片段、endpoint、用户 query、回答正文、来源 URL 集合或完整 provider body。

## 3. 版本证据

### 3.1 结论

本次现场版本分类为：

```text
PROBABLE_REVISION_6
```

不是 `CONFIRMED_REVISION_6`，因为日志没有直接输出以下不可歧义的版本元数据：

```text
ToolPkg version = 1.0.5
response schema revision = 6
environment variable count = 20
ToolPkg artifact digest
```

### 3.2 支持 revision 6 的证据

- `com.kiyori.openai_web_search` 设置 UI 能正常 dispatch package-bound action
- `openai_web_search` 插件注册并成功激活
- 公开工具名为 `openai_web_search:search`
- 现场返回 `RELAY_RESPONSE_TEXT_ONLY` 和 revision 6 的结构化 host 错误
- 日志没有出现 `MODEL_CONFIG`、`modelConfigId` 或旧模型配置错误码
- 29 次调用、参数边界和结果行为与当前 revision 6 ToolPkg 源码完全吻合
- 当前源码的 schema revision 是 `6`，ToolPkg 是 `1.0.5`

### 3.3 仍不能证明的内容

- 日志没有证明设备加载的 ToolPkg 字节与当前本地生成制品一致
- 日志没有记录成功结果中的 `schema_version`
- 日志没有记录 `ows_*` request ID、response ID 或 lifecycle snapshot
- 用户提供的 provider request ID 没有进入这份 Kiyori 日志

revision 7 应把 package version、schema revision、manifest digest 和 artifact source 作为脱敏注册
元数据记录，避免后续现场分析继续依赖行为推断。

## 4. 29 次现场调用时间线

### 4.1 第一批：5 次

```text
工具发现：19:23:03.020
结果收集：19:25:29.289
调用数量：5
批次结果：全部成功
发现到收集：146269ms
```

这批包含基础和核心成功场景。日志没有为每个搜索输出 request ID 或阶段耗时，因此不能进一步拆分
为排队、HTTP、provider 搜索和 parser 时间。

### 4.2 第二批：8 次

```text
工具发现：19:26:14.066
结果收集：19:27:52.131
调用数量：8
批次结果：全部成功
发现到收集：98065ms
```

这批同样不能从日志拆出单请求耗时。批次等待可能同时包含 ToolPkg 调度、共享 admission、远端执行
和 callback 投递。

### 4.3 第三批：8 次

```text
工具发现：19:28:31.734
结果收集：19:28:56.549
调用数量：8
批次结果：3 成功、5 失败
发现到收集：24815ms
```

五个失败分别是：

1. 非预期：relay 返回 answer text，但没有 URL citation、URL action source 或已识别 structured
   feed，当前 parser 返回 `RELAY_RESPONSE_TEXT_ONLY`
2. 预期：allowed 与 blocked 域名重叠
3. 预期：域名数组输入不是合法 JSON
4. 预期：`context_size` 不在允许集合
5. 预期：query 为空

### 4.4 第四批：8 次

```text
工具发现：19:29:27.750
结果收集：19:30:19.202
调用数量：8
批次结果：7 成功、1 失败
发现到收集：51452ms
```

失败项是预期的“域名参数包含完整 URL 和 path”。宿主按当前纯域名合同正确拒绝。

### 4.5 不能作为搜索耗时的日志

四个 `enhanced.processToolResults.complete` 在 19:35:18.892 至 19:35:18.895 几乎同时输出：

```text
第一批 subtree：589602ms
第二批 subtree：446762ms
第三批 subtree：382344ms
第四批 subtree：299692ms
```

这些数字不是对应搜索批次的远端执行耗时。`processToolResults()` 会继续调用主模型，再递归进入下一轮
工具执行；外层 finally 只有整棵后续工具链返回后才结束。因此这些值表示“从当前工具结果开始到其
所有后代回合结束”的 subtree 时间。

revision 7 应保留这一链路总时间，但必须改名并增加 round、depth 和 invocation ID；每批搜索的
真实耗时应由 Hosted Web Search lifecycle 单独返回。

## 5. P0：域名过滤不是可声明的访问安全边界

### 5.1 现场证据

用户的 blocked-domain 场景中，最终 citation 没有使用被屏蔽域名，但 `search_actions` 仍出现面向
该域名的限定搜索和直接 `open_page`。

这说明当前现场 relay 至少在 action 审计通道中违反了 blocked-domain 意图。仅过滤最终 citation
不能满足隐私、合规或安全场景的“不得访问”语义。

### 5.2 已确认的客户端根因

当前请求链：

```text
OpenAIHostedWebSearchPolicy.compileEffectiveRequest()
	→ 生成 effective allowed_domains / blocked_domains
OpenAIHostedWebSearchRequestCompiler.compile()
	→ 把非空 filters 发送给 provider
OpenAIHostedWebSearchResponseParser.parseWithDiagnostics()
	→ 收集 action URL、action sources、open_page 和 citations
	→ 不验证这些 URL 是否满足 effective domain policy
	→ source_diagnostics 只返回 allowed_domains
	→ 违反 blocked_domains 的响应仍可作为 success 返回
```

客户端当前没有：

- `requested_blocked_domains`
- `effective_blocked_domains`
- `requested_allowed_domains`
- `effective_allowed_domains`
- action/source/citation 的域名策略审计
- blocked-domain 违规错误码
- relay 域名过滤能力状态

### 5.3 安全边界

客户端只能审计 provider 在响应中公开的 action、source 和 citation。它无法证明 provider 没有：

- 访问后未上报某个 URL
- 在远端跟随未上报的 redirect
- 使用未进入 action trace 的缓存、索引或内部抓取数据

因此“响应后未发现违规”只能称为：

```text
REPORTED_ACTIONS_COMPLIANT
```

不能称为：

```text
NO_BLOCKED_DOMAIN_WAS_ACCESSED
```

严格访问前保证必须来自可信 provider 合同或独立网络控制面，不能由 Android 客户端事后检查伪造。

### 5.4 revision 7 最佳方案

#### 官方 Hosted Web Search

`RESPONSES_HOSTED_OFFICIAL` 可以依赖官方参数合同发送 domain filters，同时执行客户端 fail-closed
响应审计：

- 任一 `open_page` URL 命中 blocked domain，结果失败并被扣留
- 任一 action source 或 citation URL 命中 blocked domain，结果失败并被扣留
- allowed domain 非空时，任一 URL host 不在 allowlist 中，结果失败并被扣留
- `search` action 中明确的 `site:<domain>` 限定命中 blocked domain，结果失败
- 错误码使用 `DOMAIN_POLICY_VIOLATION`
- 不自动修改 filters 后重新提交
- 不返回可能由违规访问产生的 answer

#### Responses-compatible relay

当前 `RESPONSES_RELAY_STRICT` 没有可验证的访问前安全保证。revision 7 不应继续把相同参数描述为
硬安全能力。

第一阶段采用 fail-closed：

```text
relay + 任意 allowed_domains / blocked_domains
	→ DOMAIN_FILTER_UNSUPPORTED_FOR_RELAY
	→ submission_state=not_sent
```

未来若 relay 提供正式、可核实且可审计的过滤合同，应独立设计 relay capability profile。单次
compatibility probe 只能提供行为样本，不能升级成访问前安全保证。

不要增加“relay 可能支持所以继续发送”的兼容路径，也不要把 advisory filtering 混入现有硬过滤
字段。

### 5.5 域名匹配合同

统一 owner 应提供：

```text
normalizeDomain()
hostMatchesDomain()
auditDomainPolicy()
```

规则：

- host 和 domain 使用 ASCII、lowercase 形式
- 去除尾点
- 默认端口不参与 host 身份
- `example.com` 匹配自身和 `*.example.com`
- `notexample.com` 不匹配 `example.com`
- 大小写不影响结果
- URL userinfo 继续拒绝
- 完整 URL 不能作为 domain 参数
- 搜索 action 只解析明确的 `site:` 操作符，不扫描普通自然语言中的域名文本

### 5.6 诊断

成功或失败都应返回：

```text
requested_allowed_domains
effective_allowed_domains
requested_blocked_domains
effective_blocked_domains
domain_policy_contract
domain_policy_status
domain_policy_violations
redirect_visibility
```

违规项只包含：

```text
channel
action_index
action_type
normalized_domain
```

不得把完整 query 或整组 URL 复制到普通日志。

## 6. P1：URL 双重编码与来源身份错误

### 6.1 已确认的双重编码根因

当前 `normalizeOpenAIHostedWebSearchUrl()`：

1. 使用 `URI(rawUrl)` 解析
2. 读取 `uri.rawPath`
3. 把 `rawPath` 传给多参数 `URI(...)` 构造器
4. 再调用 `toASCIIString()`

多参数构造器把传入 path 当作未编码文本。`rawPath` 中已有的 `%` 会再次被编码为 `%25`。

本地只读 JShell 复核确认：

```text
%28  → %2528
%2B  → %252B
UTF-8 percent bytes → 每个 % 都变为 %25
```

这与现场数字查询中的 `%2528` 完全一致，是客户端确定性缺陷，不是 relay 偶发输出。

### 6.2 已确认的误报警告根因

当前 parser 使用 normalized URL 完整字符串作为：

- action source Map key
- citation Map key
- open-page Set key
- source ID 分配 key
- `CITATION_NOT_IN_ACTION_SOURCES` 差集 key

当前 normalization：

- lowercases scheme 和 host
- 移除默认端口
- 移除 fragment
- 保留全部 query
- 没有 tracking parameter 规则
- 没有独立 canonical identity

因此同一页面的以下 URL 被视为两个不同来源：

```text
page
page?utm_source=...
```

`open_page_urls` 虽然被单独收集，但没有加入 citation/action source 等价判断。现场稳定出现
`CITATION_NOT_IN_ACTION_SOURCES` 是当前 key 设计的直接结果。

### 6.3 尾部标点

结构化 URL 字段末尾的句号可能是 provider 数据，也可能是中转层的文本抽取污染。客户端不能无条件
删除所有 path 尾点，因为尾点在 URI path 中可以合法存在。

revision 7 采用保守规则：

- 若删除单个尾部 prose punctuation 后，能与另一个 action/source/citation 的 canonical identity
  精确相等，则复用另一通道的可信 URL
- 若没有对应 URL，不静默改写，返回 `SUSPICIOUS_URL_TRAILING_PUNCTUATION`
- 可疑 URL 继续作为诊断显示，但默认不作为引用 canonical URL
- 不通过联网探测 URL 有效性，避免额外请求污染搜索证据

### 6.4 单一 URL owner

新增候选类型：

```text
OpenAIHostedWebSearchUrlIdentity
OpenAIHostedWebSearchNormalizedUrl
```

至少包含：

```text
display_url
identity_key
normalized_host
normalization_flags
```

`display_url` 与 `identity_key` 必须分离：

- display URL 用于点击和输出
- identity key 只用于去重、跨通道比较和 domain policy

实现优先复用项目已有 OkHttp `HttpUrl`，不要再次用多参数 `URI` 重建 raw path。

### 6.5 canonical identity 规则

- scheme 和 host lowercase
- IDN host 使用稳定 ASCII 形式
- 删除 fragment
- 删除默认端口
- 保留路径语义
- 百分号十六进制使用统一大小写
- 只解码 RFC 3986 unreserved 字符
- 不解码 `%2F`、`%3F`、`%23` 等结构字符
- 不把 query 中的 `+` 当作 path 空格
- 移除明确的 tracking 参数，例如 `utm_*`、`gclid`、`fbclid`
- 不删除未知 query 参数
- 不通过排序或解码改变重复参数语义

同一 identity 存在多个 display URL 时，选择优先级：

1. 不含 tracking 参数的 open-page/action-source URL
2. 不含 tracking 参数的 citation URL
3. 其他有效 URL

### 6.6 必须覆盖的 fidelity 测试

```text
%28
%2B
%2F
UTF-8 / Unicode path
query 中的 &
重复 query 参数
默认端口
fragment
尾斜杠
utm_source
大小写 host
尾点 host
路径末尾句号
路径末尾右括号
```

断言重点：

- 输出中不出现非输入导致的 `%25`
- canonical equality 不改变点击 URL 的语义
- citation 能复用已有 canonical source ID
- tracking-only 差异不再产生 `CITATION_NOT_IN_ACTION_SOURCES`
- `open_page`、action source 和 citation 使用同一个 URL owner

## 7. P1：零证据结果被错误提升为硬失败

### 7.1 根因

当前 relay parser 逻辑是：

```text
answer text 非空
且没有 URL citation
且没有 URL action source
且没有 structured feed
	→ RELAY_RESPONSE_TEXT_ONLY
```

它无法区分：

- 合法的零结果
- relay 丢失 citation/source
- provider 给出未引用回答

当前行为选择直接丢弃 answer，因此现场“明确要求没有证据就说明没有”的场景无法返回模型实际说明。

### 7.2 revision 7 结果模型

新增 evidence mode：

```text
none
```

返回：

```json
{
  "success": true,
  "evidence_mode": "none",
  "answer": "provider 返回的无证据说明",
  "answer_with_source_markers": "与 answer 一致",
  "citations": [],
  "cited_sources": [],
  "all_sources": [],
  "warnings": ["NO_WEB_EVIDENCE"]
}
```

成立条件：

- Responses status 为 completed
- 至少存在一个 `web_search_call`
- answer text 非空
- 没有有效 URL citation、URL action source或 structured feed

这不表示 answer 已被证据支持。结果卡和主模型 prompt projection 必须明确标记：

```text
No verifiable web evidence was returned.
```

compatibility probe 继续要求真实 URL citation。`evidence_mode=none` 不能使 relay compatibility
通过。

### 7.3 为什么不继续硬失败

传输成功、搜索工具已调用和“没有可验证证据”是三个不同事实。用一个 hard failure 表示全部状态会：

- 丢失有用的 no-result 说明
- 让调用方无法区分无结果与网络失败
- 诱导用户重复搜索
- 无法表达 provider evidence omission

revision 7 不猜测自然语言是否真的表示“无结果”。它只忠实表达“当前响应没有可验证 evidence”，
由 warning 保留不确定性。

## 8. P1：损坏的空引用进入用户可见答案

### 8.1 根因

当前 parser：

- 原样拼接 provider `output_text`
- 只根据真实 citation annotation 插入 `[S*]`
- 不清理 provider answer 中已有的空 Markdown link

因此没有 annotation 的 `[]()` 会原样进入 `answer`。`citations` 数组为空并不能自动清理正文。

### 8.2 正确处理顺序

新增候选 owner：

```text
OpenAIHostedWebSearchAnswerNormalizer
```

顺序：

1. 保留 provider 原始 answer 作为内部 citation span 审计基线
2. 验证所有 annotation offset
3. 对用户可见 answer 执行有界 Markdown 清理
4. 建立原始 UTF-16 index 到清理后 index 的映射
5. 重新映射 citation span
6. 从同一清理后 answer 生成 `answer_with_source_markers`

不直接对已经插入 source marker 的字符串做二次正则替换。

### 8.3 清理不变量

- 用户可见 answer 不包含空 Markdown link 或空 Markdown image
- fenced code 和 inline code 中的字面量不被改写
- 每个 `[S*]` 都能映射到 `cited_sources`
- `answer` 与 `answer_with_source_markers` 使用同一个 normalized base
- 清理不能改变 citation 所覆盖的实际文本
- 无法安全映射时返回结构化 `CITATION_INVALID`，不显示损坏语法

## 9. P1：日志隐私修复未覆盖所有 owner

### 9.1 已确认泄漏

`ModelApiSettingsSection.flushSettings()` 当前日志模板使用：

```text
apiKey.take(5)
完整 apiEndpoint
```

现场因此四次记录了 Key 前缀片段和完整自定义 endpoint。

`MessageProcessingDelegate.finalizeMessageAndNotify()` 当前在自动朗读关闭时仍记录：

```text
finalContent length
speechPreview(finalContent)
```

现场记录了用户可见回答片段。

### 9.2 与旧 W2 的关系

上一轮 W2 已修复：

- `ApiKeyProvider` 前后缀日志
- provider 请求正文
- provider error body

但测试范围没有覆盖：

- 模型设置保存 UI
- 自动朗读 final preview
- 其他 voice provider 的 speech preview
- ChatViewModel 的语音清理和 segment preview

因此旧 W2 是已完成的局部修复，不是全应用日志隐私封板。

### 9.3 revision 7 方案

- 设置保存日志只记录 provider type、model count、credential configured 和 endpoint kind
- 不记录 Key 任何片段
- 不记录完整 custom endpoint
- 如确需关联同一 endpoint，使用不可逆、配置域内的短 revision
- 自动朗读和 TTS 日志只记录长度、segment index、provider class 和状态
- 删除正文 preview，不以 digest 替代正文
- ToolPkg parser-invalid 和 domain violation 日志继续禁止 query、answer、sources 和 URL 集合

新增静态和行为测试：

```text
ModelApiSettingsLogPrivacyTest
SpeechLogPrivacyTest
OpenAIWebSearchFieldLogPrivacyTest
```

## 10. P2：参数类型与错误合同

### 10.1 当前问题

ToolPkg 元数据当前声明：

```text
allowed_domains          string
blocked_domains          string
use_configured_location  string
```

TypeScript 再执行：

```text
JSON.parse(domain string)
string → boolean
```

项目 ToolPkg runtime 本身已经支持：

```text
array
boolean
number
integer
object
string
```

因此当前二次 JSON 不是运行时限制，而是插件公开合同选择错误。

非法域名 JSON 在 QuickJS 中直接抛出原生 `SyntaxError`，绕过宿主的结构化
`OpenAIHostedWebSearchException`。

### 10.2 revision 7 参数合同

```text
query                    string, required
context_size             string enum, optional
allowed_domains          array<string>, optional
blocked_domains          array<string>, optional
use_configured_location  boolean, optional
```

TypeScript：

- 删除 `parseDomainArray()`
- 删除 `parseBoolean()`
- 使用精确 `string[]` 和 `boolean`
- 不保留旧 string 输入分支
- 删除只为记录并重新抛出的 `catch`
- service callback 成为错误终态唯一 owner

### 10.3 ToolPkg 字符串传输边界

当前 `ToolParameter.value` 是字符串，ToolPkg runtime 根据 metadata type 做严格转换。对于 array 和
boolean，它能拒绝非法文本。对于声明为 string 的 query，原始数字 `123` 和字符串 `"123"` 在进入
插件前可能都已成为文本。

搜索 query 本身允许是纯数字文本，因此 revision 7 不应通过“内容看起来像数字”拒绝合法搜索。

若未来必须区分 provider 原始 JSON number 与 string，需要在 `AITool` / `ToolParameter` 层保留 typed
argument provenance。这是全局 ToolPkg ABI 变更，不能在 Web Search JavaScript 内伪造严格类型。

### 10.4 统一错误结构

新增调用参数错误：

```text
INVALID_ARGUMENT
```

字段：

```text
field
reason
message
submission_state=not_sent
request_id
```

`reason` 使用稳定枚举：

```text
MISSING
INVALID_TYPE
INVALID_VALUE
CONFLICT
TOO_LARGE
```

不重新加入 `retryable`。revision 6 删除该字段是为了避免把未知提交状态误导为自动重试许可。

环境变量错误继续使用配置类错误；调用参数错误不能再复用 `CONFIG_SOURCE_INVALID`。

### 10.5 Runtime 转换错误

`JsToolManager` 在 JavaScript 执行前发生的类型转换失败，应返回通用结构化 ToolPkg invocation
error，至少包含 tool name、field、expected type 和 stable code。不得把 raw JSON parser message
直接暴露给用户。

这是一个窄的共享 runtime 改动，不改变其他 ToolPkg 参数的成功语义。

## 11. P2：成功结果缺少性能、位置与策略遥测

### 11.1 当前已有但没有输出的状态

`OpenAIHostedWebSearchRequestLifecycle` 已拥有：

- phase
- submission state
- cancellation owner
- elapsed
- configured timeout
- queue wait
- provider request ID

这些字段只进入失败 envelope。成功结果丢失相同诊断，因此现场无法比较：

- queue wait
- HTTP 总时间
- response header wait
- response body read
- parser 时间
- callback 投递

### 11.2 revision 7 execution diagnostics

成功和失败统一返回：

```text
total_elapsed_ms
queue_wait_ms
http_elapsed_ms
response_header_wait_ms
response_body_read_ms
parse_ms
callback_delivery_ms
provider_request_id
submission_state
```

Request lifecycle 记录单调时间点，不使用 wall clock 计算 duration。

### 11.3 位置诊断

不返回精确 city、region、timezone 或完整 location JSON。只返回：

```text
location_requested
location_configured
location_applied
location_precision
```

`location_precision`：

```text
none
country
region
city
timezone
mixed
```

该诊断只能证明 Kiyori 是否把配置写入请求，不能证明 provider 实际用它改变了检索结果。

### 11.4 来源规模与 token

现场简单查询也可能返回大量 sources，并产生很高的 provider input token usage。客户端 canonical
去重可以减少重复来源和主模型后续上下文，但不能降低 provider 已经发生的内部搜索 token。

revision 7 schema 分离：

```text
cited_sources
all_sources
source_summary
```

- `cited_sources` 只包含 citation 实际引用的 canonical source
- `all_sources` 保存完整去重后的 action/source/open-page 证据
- UI 使用完整 `all_sources`
- 发送给主聊天模型的 tool-result projection 默认只包含 answer、markers、cited sources、
  source counts、warnings 和必要 diagnostics
- 完整 all sources 仍保存在 ToolResult 和专用结果卡，不因 prompt projection 丢失

这不是截断 provider 原始结果，也不是失败降级。它是在已有完整结果上建立两个明确消费者投影：

```text
UI evidence projection
main-model context projection
```

## 12. P2：ToolPkg 错误重复记录

### 12.1 现场计数

六个失败产生：

```text
6 × search failed
6 × DETAILED JS ERROR
6 × JS ERROR
= 18 ERROR
```

这是日志中全部 ERROR。没有十八个独立失败。

### 12.2 当前 owner

- 插件 `catch` 执行 `console.error("[openai_web_search] search failed", error)`
- QuickJS runtime 调用 `reportErrorForCall()` 记录完整类型、message、line 和 stack
- `setCallError()` 再记录带代码上下文的 `JS ERROR`

### 12.3 revision 7 方案

- 删除插件只记录并重新抛出的 catch
- `setCallError()` 成为 call terminal error owner
- `reportErrorForCall()` 只向 call session 附加 detail，不重复写全局 ERROR
- 按 `callId + stable error code + phase` 保证一次完整 cause
- 预期 `INVALID_ARGUMENT` 记录为 INFO 或 WARN，不记录完整 stack
- runtime、bridge 或未知 JavaScript 错误保留一次 ERROR 和一次受限 stack
- 不吞掉异常，不把失败改成成功 ToolResult

## 13. P2：搜索批次计时与递归工具链计时混淆

当前 `enhanced.processToolResults.complete` 实际表示整棵后续递归工具链结束。revision 7 应：

- 改名为 `enhanced.toolSubtree.complete`
- 增加 `round`
- 增加 `depth`
- 增加 `invocationId`
- 保留 `resultCount`
- 单独记录当前 follow-up provider 的 first chunk 和 completion
- 由 Hosted Web Search result 自身提供单请求和批次时间

不要把 299–590 秒 subtree 时间显示在 Web Search 卡片中。

## 14. P2/P3：启动阶段重复主线程延迟

### 14.1 已确认事实

本次没有系统 ANR。`AnrMonitor` 在约 1.46 秒内记录五个连续启动延迟样本：

```text
506ms
607ms
548ms
657ms
788ms
```

前两个发生在首帧前，后三个发生在首帧后。

同一窗口内发生：

- ToolPkg 注册
- 首帧后完整初始化提交
- ChatServiceCore / EnhancedAIService 初始化
- MCP 和 package 扫描
- UIHierarchy 预绑定
- 图片、媒体和数据库预加载

日志没有线程 CPU、主 Looper message owner 或每个 ToolPkg 注册耗时，不能把卡顿根因直接归到任一
任务。TextSegmenter 在后台线程耗时 `5183ms`，当前没有证据证明它直接阻塞主线程。

### 14.2 下一步

先补充：

```text
main looper message owner
ToolPkg registration duration
registration thread
package source
first-frame relative timestamp
startup phase CPU / wall distinction
```

采集多次冷启动样本后再决定是否调整 ToolPkg 注册、服务初始化或后台预热顺序。当前不因单份日志
移动初始化 owner。

## 15. NO_ACTION 与噪声

以下内容本轮不作为缺陷修复：

- `UIHierarchyManager` 的 `BIND_RETURNED_FALSE` 已使用明确状态记录，没有误报成功
- “chat 没有绑定 workspace”是当前会话状态 warning，不影响 Web Search
- 回合末尾一个未完成 tool call 按 `user_boundary` 取消后，最终 turn 仍正常 completed；单次样本
  不足以证明 provider-native tool identity 回归
- structured `oai-weather` 没有 URL 是合法 evidence mode，不应伪造网页 citation
- `CITATION_NOT_IN_ACTION_SOURCES` 作为概念仍有价值；修复后只应表示 canonical identity 仍不一致
  的真实差集

## 16. Revision 7 响应合同

revision 7 候选版本：

```text
ToolPkg version = 1.0.6
response schema revision = 7
```

成功 envelope：

```json
{
  "success": true,
  "schema_version": 7,
  "request_id": "opaque request id",
  "response_id": "provider response id",
  "provider": "openai",
  "backend": "responses_web_search",
  "mode": "live",
  "model": "configured model",
  "evidence_mode": "url_citations_and_action_sources",
  "answer": "normalized answer",
  "answer_with_source_markers": "normalized answer with source markers",
  "search_actions": [],
  "citations": [],
  "cited_sources": [],
  "all_sources": [],
  "source_summary": {},
  "usage": {},
  "warnings": [],
  "source_diagnostics": {},
  "execution_diagnostics": {}
}
```

错误 envelope：

```json
{
  "success": false,
  "error": {
    "code": "INVALID_ARGUMENT",
    "field": "allowed_domains",
    "reason": "INVALID_TYPE",
    "message": "sanitized message",
    "request_id": null,
    "submission_state": "not_sent"
  }
}
```

不保留 revision 6 `sources` 字段的并行别名。Kiyori 尚未发布，schema revision 已提供明确版本边界；
UI parser、ToolPkg types、tests 和文档应在同一修改中原子升级。

## 17. 实施工作包

### R7-M0：冻结合同和失败 fixture

- 把本报告中的六个失败、URL 双重编码、tracking-only 差异和空引用建立为本地 fixture
- fixture 使用脱敏 synthetic URL，不复制现场 endpoint、query 或 provider body
- 锁定当前 revision 6 失败行为，避免修复过程中误判

完成信号：

```text
fixture 能稳定复现每个客户端确定问题
每个 fixture 不调用外网
```

### R7-M1：URL identity 与 domain policy

候选文件：

```text
OpenAIHostedWebSearchModels.kt
OpenAIHostedWebSearchPolicy.kt
OpenAIHostedWebSearchRequestCompiler.kt
OpenAIHostedWebSearchResponseParser.kt
OpenAIHostedWebSearchUrlIdentity.kt
```

顺序：

1. 建立单一 URL owner
2. 修复 percent fidelity
3. 建立 canonical identity
4. 建立 domain matcher
5. 对官方响应执行 fail-closed audit
6. relay filters 在提交前拒绝
7. 增加 requested/effective/violation diagnostics

完成信号：

```text
blocked-domain 违规不能返回 success
relay filter request 不产生 HTTP call
所有 fidelity 测试通过
```

### R7-M2：零证据与 answer normalization

候选文件：

```text
OpenAIHostedWebSearchModels.kt
OpenAIHostedWebSearchResponseParser.kt
OpenAIHostedWebSearchAnswerNormalizer.kt
OpenAIHostedWebSearchEvidence.kt
OpenAIWebSearchToolResultDisplay.kt
```

完成信号：

```text
合法零证据返回 evidence_mode=none
compatibility probe 仍要求 URL citation
空 Markdown 引用不再进入用户可见 answer
citation offsets 在清理后仍正确
```

### R7-M3：ToolPkg 参数与错误合同

候选文件：

```text
examples/openai_web_search/src/packages/openai_web_search.ts
examples/types/toolpkg.d.ts
JsToolManager.kt
ToolPkgOpenAIWebSearchBridge.kt
OpenAIHostedWebSearchModels.kt
```

完成信号：

```text
domain 参数是 array
location 开关是 boolean
非法参数不再泄露 QuickJS SyntaxError
INVALID_ARGUMENT 包含 field 和 reason
```

### R7-M4：来源投影、位置和性能遥测

候选文件：

```text
OpenAIHostedWebSearchRequestLifecycle.kt
OpenAIHostedWebSearchGateway.kt
OpenAIHostedWebSearchModels.kt
OpenAIHostedWebSearchEvidence.kt
ConversationMarkupManager.kt
OpenAIWebSearchToolResultDisplay.kt
```

完成信号：

```text
成功与失败都有一致 timing
UI 保留全部 canonical evidence
主模型 projection 不重复发送全部 uncited sources
location 只返回非敏感应用状态
```

### R7-M5：日志错误所有权与隐私

候选文件：

```text
examples/openai_web_search/src/packages/openai_web_search.ts
JsEngine.kt
ModelApiSettingsSection.kt
MessageProcessingDelegate.kt
语音 provider 与 ChatViewModel 的 speech preview owner
```

完成信号：

```text
一次 ToolPkg 失败最多一条全局 ERROR
预期参数错误没有完整 stack
Key 前缀、完整 endpoint 和正文 preview 的日志扫描为 0
```

### R7-M6：版本与启动可观察性

候选文件：

```text
PackageManager.kt
ToolPkg registration owner
AnrMonitor / startup observation owner
```

完成信号：

```text
现场日志可直接证明 ToolPkg version、schema revision 和 artifact digest
多次冷启动能够定位主线程延迟 owner
```

### R7-M7：文档、完整测试与 Debug APK

同步：

```text
CONTEXT.md
docs/TODO/README.md
docs/TODO/openai_hosted_web_search/
docs/doc-src/architecture/openai_hosted_web_search.md
ToolPkg dist/
```

本地门禁：

1. TypeScript deterministic generation 和 strict
2. URL/domain/zero-evidence/answer-normalizer fixture
3. Hosted Web Search 完整 JVM matrix
4. ToolPkg runtime parameter conversion tests
5. 日志隐私与错误所有权 tests
6. Kotlin compile
7. formal readiness
8. `git diff --check`
9. 串行 Debug APK 构建与 ToolPkg/APK 一致性审计

## 18. 现场验收矩阵

实现完成后，在用户明确授权真实 relay、可能计费请求和设备操作时执行。

### 18.1 官方合同

- blocked exact domain
- blocked subdomain
- allowed exact domain
- allowed subdomain
- tracking query canonicalization
- percent-encoded path
- zero result
- structured feed

### 18.2 Relay

- 不带 filters 的普通搜索
- 任何 filters 在本地 `not_sent` 拒绝
- compatibility probe 继续验证真实 URL citation
- 不自动换 endpoint、模型、Key 或 backend

### 18.3 UI

- `evidence_mode=none`
- domain policy violation
- 大量 all sources 和少量 cited sources
- 空 citation 清理
- 320dp、字体放大、中英文和 TalkBack

每个真实请求只执行一次。发生 `submission_unknown` 时停止该 case，不进行第二次相同 POST。

## 19. 禁止方案

- 不把 blocked-domain 违规响应过滤后继续标记 success
- 不把 response 后审计描述成访问前防火墙
- 不为 relay 保留“可能支持”的隐式 filters 路径
- 不通过自动重试、删字段重试、换 endpoint、换模型、换 Key 或换 Provider提高成功率
- 不用字符串替代 array/boolean 的正式类型
- 不用正则全局解码 URL
- 不无条件删除所有 URL 尾部标点
- 不从 answer 文本猜 URL、citation 或 no-result 状态
- 不把全部 all sources 直接丢弃
- 不重新加入通用 `retryable`
- 不吞掉 QuickJS、parser 或 provider 异常
- 不用新增 suppress、Lint baseline 或跳过测试收口

## 20. 最短后续入口

后续正式开发从 `R7-M0` 和 `R7-M1` 开始。第一批代码写入前应再次确认：

- `main`、HEAD、origin/main 和 dirty worktree
- revision 6 的用户现有改动仍完整保留
- 用户仍授权生产源码修改
- 不提交、不推送边界
- 真实 relay、设备、模拟器和 ADB 是否仍禁止

上文第 1 节至第 19 节保留本轮现场分析时的历史记录，包括“本轮只执行日志读取、源码与测试
合同审计、架构设计和文档写入”的当时边界；该历史描述不覆盖后续正式实施段。后续 revision `7`
代码、测试、ToolPkg 和文档收口已经完成，当前交付状态是：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_REVALIDATION_PENDING
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
NO_COMMIT
NO_PUSH
```
