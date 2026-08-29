---
document_type: architecture
status: implemented_local_verification
verification_boundary: device_pending
---

# AI 对话详情与完整审计

## 目标与范围

Kiyori 为每条已保存的 AI 对话维护一条持久化审计链，并在 AI 对话页右上角提供“对话详情”
入口。该能力用于：

- 追踪用户输入怎样经过附件、工作区、记忆、Prompt Hook、ToolPkg 和上下文拼装后进入
  Provider；
- 保存 Provider 可观察语义事件、聚合文本增量、工具调用、完整异常和终态；
- 解释当前聊天气泡与原始消息、修订、回答 variant、删除、回滚和分支之间的关系；
- 导出可供开发者或 Codex 等 AI 工具审阅的完整审计包与隐私增强 Markdown。

审计只记录 Kiyori 实际构造、接收或执行的事实，不宣称能够看到 Provider 未返回的内部推理。
TCP 分片、心跳、轮询、页面停留时间和其他无业务意义的活动不进入审计。

## 所有权

`ConversationAuditRepository` 是以下状态的唯一写入 owner：

- 不可覆盖审计事件；
- 加密 payload 与事件引用；
- 消息修订与当前投影指针；
- 完整性状态、哈希链与签名封印；
- 历史重建、分支继承、可移植导入和存储回收。

现有数据继续承担各自职责：

- `messages` 与 `message_variants` 是聊天当前投影；
- `provider_executions`、`provider_execution_events`、`message_provider_states` 和
  `tool_invocation_ledger` 是可恢复 Provider/工具执行状态；
- 审计账本保存上述状态变化所代表的不可覆盖事实，不创建第二套聊天或执行 owner。

## 数据模型

Room 数据库版本 24 包含七张审计表，并在 `chats`、`messages` 与
`message_variants` 上保存 provider usage v2 的请求覆盖计数、互斥输入 token 桶、输出、
reasoning 细分以及仅覆盖已报告 cache metric hop 的 prompt 分母。22→23 增加 provider usage
projection；23→24 增加 `providerCacheMetricPromptTokens`，并把无法恢复该分母的旧
`providerCacheMetricRequestCount` 重置为未报告。旧行中的零值表示历史版本未提供对应 provider
指标，不表示供应商明确报告了零缓存命中。

| 表 | 责任 |
| --- | --- |
| `conversation_audits` | 每条聊天的 schema、完整性、事件游标、链头和封印游标 |
| `conversation_audit_events` | 按 `(chatId, sequenceNumber)` 单调追加的事件元数据 |
| `conversation_audit_payloads` | 内容寻址、压缩和加密后的 payload 元数据 |
| `conversation_audit_event_payloads` | 事件与一个或多个 payload 的有序引用 |
| `conversation_message_revisions` | 用户或 AI 可见正文的不可覆盖修订链 |
| `conversation_message_projections` | 每条消息或 variant 当前生效的 revision 指针 |
| `conversation_audit_seals` | 对指定链头的 ECDSA 签名封印 |

`ChatDao.insertChat` 使用 `@Upsert`，避免 Room `REPLACE` 删除旧聊天行并通过外键级联误删审计。

## 写入与一致性

### 事件链

每个事件包含前一事件 SHA-256、当前序号、事件元数据和有序 payload 引用。事件哈希成为下一
事件的前驱，并更新 `conversation_audits.chainHeadSha256`。同一聊天的写入由仓储级 Mutex
串行化，Room 通过期望序号与期望链头进行比较更新，游标被并发推进时当前事务失败。哈希材料
绑定 `eventId`、`recordedAt` 和 payload 的媒体类型、编码、明文字节数等元数据，不能只通过
保持正文哈希不变来篡改事件身份或载荷解释方式。

### 业务投影与审计

会改变聊天投影的操作使用 `mutateAndAppendEvent`：

1. payload 在应用私有目录原子提交；
2. Room 事务执行消息、variant 或删除变更；
3. 同一事务插入事件、payload 引用并推进链头；
4. 任一步失败时 Room 变更整体回滚；
5. 失败后产生的无引用 payload 由维护回收器删除。

消息修订还会在同一事务中固化 revision 0、写入新 revision、更新 projection，并更新当前
`messages` 或 `message_variants` 正文。历史实际 Token、Provider 请求和时间指标不会被改写。
历史重建和完整审计导入也使用单一 Room 事务提交其全部事件、引用、修订、投影、续接事件与封印；
任一步失败时不会留下半条可被误判为完成的链。

### 关键请求门禁

`ConversationAuditProviderRequestRecorder` 在两个真实 Provider hop 入口之前同步写入
`FINAL_PROVIDER_SEMANTIC_REQUEST`。该事件包含：

- 最终 `requestHistory`，含 system/user/assistant/tool/summary turn 和 metadata；
- 当前模型参数；
- 完整可用工具 schema；
- Provider/模型、思考和流式开关；
- `ProviderRequestContext` 的 chat、message、variant、local execution 与 hop 身份；
- 不包含真实 API Key、Key 池值、Authorization、Cookie 或认证头原文的模型配置。

审计与真实 `AIService.sendMessage()` 复用同一个 `ProviderRequestContext`。关键快照无法落盘时，
Provider 调用不会执行。

## 输入、Provider 和工具接线

普通发送记录：

1. `USER_INPUT_SUBMITTED`：输入框原始文本和原始附件引用；
2. `USER_INPUT_TRANSFORMED`：代理发送者、回复引用、附件、工作区等处理后的输入；
3. 每个 Prompt Hook 的开始、完成或完整异常；
4. `FINAL_PROVIDER_SEMANTIC_REQUEST`；
5. Provider execution、Provider 语义事件和脱敏原始 payload；
6. 按约一秒聚合的 `PROVIDER_TEXT_DELTA` 或 `PROVIDER_TEXT_REVISION`；只有新快照以前一快照为
   前缀时才记录增量，Provider 改写已输出前文时保存完整修订；
7. 工具请求、开始、每个语义结果、完成或完整异常；
8. Provider 取消、错误或成功终态；
9. 最终 AI 当前投影与实际用量。

OpenAI Responses 的可恢复事件在进入 UI 前镜像到审计链。其他 Provider 由公共
`EnhancedAIService` 和消息收集边界记录，因此最终语义请求和可见文本不依赖某一个 Provider
实现。ToolPkg AI Provider 也经过同一个公共入口。

流式正文不会逐字符创建事件。主消息层按时间窗口聚合增量，同时最终 AI 投影保存完整可见正文。

## 编辑、variant、删除与分支

- “编辑消息”创建新 revision，立即更新当前气泡和未来上下文，不自动重新请求模型；
- 现有“编辑并重发”保持为独立操作，继续执行其消息与工作区回滚合同；
- AI 编辑只影响当前选中的 variant；
- `VARIANT_CREATED`、`VARIANT_SELECTED` 和 `VARIANT_DELETED` 记录回答版本变化；
- 消息删除与批量回滚先保存被删除正文和 variant，再在同一事务删除当前投影；
- 删除/回滚事件使用 `TOMBSTONE`，原始事件不会被覆盖；
- 分支复制截至分支点的事件和 payload，重新建立子聊天自己的哈希链和封印；
- 分支后段失败会删除半完成目标聊天；若父链已经写入创建事实，则追加
  `BRANCH_CREATION_ABORTED` tombstone；
- 只有明确删除整个聊天时，Room 外键才删除该聊天审计；随后回收不再被任何聊天引用的
  payload。

## payload 安全

payload 在加密前通过 `ConversationAuditRedactor`：

- JSON 凭据键递归替换；
- Authorization、Bearer、Cookie、密码、API Key、访问令牌、私钥和签名赋值被替换；
- URL 凭据参数和 HTTP Basic user-info 被替换；
- 自定义模型参数或工具默认值的凭据语义值在构建最终请求快照时直接替换。

脱敏明文计算 SHA-256 后使用 GZIP 压缩，再由 Android Keystore AES-GCM 加密。正文密钥与链
签名密钥分离。签名使用 EC P-256 / `SHA256withECDSA`。Keystore 密钥不可用时不得伪造正文，
完整性状态进入“密钥不可用”或“内容损坏”。本机生成链段的 seal 除验证 ECDSA 数学签名外，
还必须确认公钥等于当前 Android Keystore 签名公钥；外部导入链没有本机信任锚，只能保持
`SOURCE_UNVERIFIED`。

## 完整性状态

固定状态为：

| 内部值 | 用户显示 | 含义 |
| --- | --- | --- |
| `COMPLETE` | 完整 | 原生链以已知终态结束 |
| `IN_PROGRESS` | 进行中 | 当前有真实执行或事件追加 |
| `PARTIAL` | 部分完整 | 历史或链路只能重建部分事实 |
| `BASIC` | 基础记录 | 只能重建聊天消息和 variant |
| `RECORDING_INTERRUPTED` | 记录中断 | 活跃链关键事件未能继续写入 |
| `CONTENT_CORRUPTED` | 内容损坏 | payload、哈希或签名校验失败 |
| `SOURCE_UNVERIFIED` | 来源未验证 | 导入签名数学有效，但公钥没有本机信任锚 |
| `KEY_UNAVAILABLE` | 密钥不可用 | 本机正文密钥不能读取 |

`ConversationAuditCompletenessPolicy` 允许原生链在 `COMPLETE` 和 `IN_PROGRESS` 间转换，但不会
让后续成功事件把 `PARTIAL`、`BASIC`、`SOURCE_UNVERIFIED` 或更严重状态覆盖为完整。

## 对话详情 UI

AI 对话顶栏顺序为：

```text
浏览栏 | 终端 | 对话详情 | 工作区
```

Browser 入口继续转交给唯一共享 Browser Home/WebSession Runtime，不在 AI 页面创建第二个
WebView。终端、对话详情、工作区和聊天正文由一个 `ChatPanelMode` 管理；详情再次点击返回聊天。

详情页保留 AI 顶栏，下方提供：

- **时间线**：事件一行展示，使用设置页同款页面/卡片色，支持搜索、匹配计数、逐条或全部
  展开、按需读取 payload、复制和新事件提示；
- **对话**：用户与 AI 当前可见消息一行展示，支持搜索、匹配计数、逐条或全部展开，展开后
  允许在非生成状态创建修订；编辑按稳定消息身份定位；
- **Raw**：只读 JSONL 行、行号、搜索、匹配计数、逐条或全部展开、选择和复制，展开态显示
  格式化 JSON。

默认只加载最近 100 条事件。更早事件通过 sequence 游标分页加载；新事件由最后事件观察器触发
增量查询。加载旧页不会把用户拉回底部。大 payload 仅在展开事件时解密。头部显示完整性、总事件
数、已加载数、链头、当前 Provider/模型和本聊天审计存储占用。对话视图独立读取完整持久化消息，
不复用普通聊天正文的显示窗口；编辑按消息身份定位，避免长对话窗口裁剪导致改错行。

三个视图共用紧凑的无边框搜索栏，Back 优先关闭注释/导出弹窗、展开内容、编辑弹窗和当前
搜索，最后关闭详情面板。搜索只改变当前视图，不改变导出快照范围。

## 导出、导入和备份

### `.kiyori-audit`

完整包是 ZIP 容器，固定包含：

```text
manifest.json
chat.json
audit.json
timeline.md
events.jsonl
revisions.jsonl
projections.jsonl
integrity.json
redaction-report.json
payloads/<sha256>.<txt|bin>
```

导出在同一个聊天 Mutex 内封印快照链头、完整验证事件/payload/seal/游标并获取一致性 cutoff，
避免 seal 与读取之间插入未封印尾部。包写入完成后追加 `AUDIT_EXPORTED` 并再次封印。

导入拒绝：

- 不安全路径、反斜杠逃逸、空路径段、`.`/`..` 和重复条目；
- 条目数、单条目或解压总量超限；
- manifest/chat/audit ID 或 schema 不一致；
- payload Base64、独立 payload 文件、字节数或 SHA-256 不一致；
- 悬空事件 payload、重复主键、非连续序号、错误前驱/父事件、错误 revision/projection；
- seal root、重复 seal 序号或 ECDSA 签名错误；
- 导入正文仍含可识别凭据。

导入 payload 使用目标设备 Keystore 重新加密。原事件、哈希和外部签名保持不变；随后追加本机
`IMPORTED_CONTINUATION` 并由本机重新封印。外部公钥没有本机信任锚，因此状态为“来源未验证”。

### AI 诊断 Markdown

详情页默认导出独立 `*-ai-diagnostics.md`。文件是 UTF-8 明文而非加密容器，包含一致性快照、
当前消息投影、历史 variant、修订/投影关联、可读事件时间线、已脱敏 payload 正文和机器可读
JSONL，方便直接交给外部 AI 诊断。它进一步假名化用户名、账户和设备标识、邮箱以及 Windows、
Unix 和 Android 私有路径；API Key、Authorization、Cookie、密码、私钥、访问令牌和请求签名
始终不会进入导出。

“导出完整审计包（签名 ZIP）”是独立的高级动作，扩展名保持 `.kiyori-audit`，保留事件链、
修订、投影、封印和二进制 payload，用于本机复现和导入。两个动作都在聊天 Mutex 内创建并验证
同一个一致性快照，导出结果不会受当前视图搜索或分页影响。

### 普通聊天归档

`OperitChatArchive.CURRENT_FORMAT_VERSION` 为 4。v4 在 v3 的完整审计、payload、
revision、projection 和 seal 基础上，增加聊天、消息与 variant 的 provider usage v2
字段。v1/v2/v3 继续导入并进入诚实的历史重建；缺失的新字段按零读取，语义是“历史版本未
记录”，不能解释为供应商明确报告了零缓存命中。带完整审计的 v3/v4 若与本地同 ID 聊天冲突
会拒绝覆盖，防止破坏本地原始事实。

## 历史重建

旧聊天在首次打开详情、导出或归档时按现有事实重建：

- 有更多历史 Provider 事实时标记“部分完整”；
- 只有聊天消息和 variant 时标记“基础记录”；
- 缺失字段明确记录“历史版本未记录”；
- 重建幂等并在完成后封印；
- 不推断不存在的系统提示词、Hook、工具结果或异常。

## 存储与删除

设置页显示审计聊天数、总 payload 大小、异常完整性数量和最大占用聊天。审计没有关闭开关和
自动清理开关。只要聊天仍存在，审计不会因为时间或空闲状态自动增长或自动删除。

删除整个聊天会删除其事件和私有投影；内容哈希共享的 payload 只有在没有任何事件或 revision
引用后才会删除。

## 验证边界

当前实现已经通过 JVM 单元测试、Room/迁移 AndroidTest 编译、完整 App 测试、Lint、项目
Python/architecture/formal/fresh-clone 门禁与 Debug APK 静态审计。该证据证明本地源码、
数据库合同和构建产物闭合，但不等于真实 Provider 或目标设备验收。

本地自动验证不能替代目标设备上的以下验收：

- 顶栏四按钮在窄屏、大字体和横屏下的触控与省略；
- 三视图滚动、复制、输入法和 Back；
- 旋转、进程重建、Keystore 状态变化和大对话分页性能；
- 实际 Provider、ToolPkg 和工具的现场完整链。
