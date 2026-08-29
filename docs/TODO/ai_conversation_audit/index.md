---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: in_progress
---

# AI 对话详情与完整审计

## 1. 目标与权威边界

本专项为 AI 对话增加“对话详情”入口，以及与每条已保存对话一一对应的持久化审计记录。
该记录用于：

- 还原用户输入、上下文拼装、Prompt Hook、ToolPkg、Provider、工具与错误的可观察链路；
- 解释聊天气泡当前内容与历史原文、回答 variant、删除、回滚和编辑之间的关系；
- 生成可由用户、开发者和 Codex 等 AI 工具直接审阅的完整导出；
- 在不保存真实凭据的前提下，为工具缺陷、Provider 异常和消息渲染问题提供可复现证据。

`ConversationAuditRepository` 是审计事件、消息修订、完整性状态和审计导出的唯一写入 owner。
现有 `messages`、`message_variants` 是当前聊天投影；`provider_executions`、
`provider_execution_events`、`message_provider_states` 和 `tool_invocation_ledger` 是
Provider 可恢复执行状态。两者都可以向审计 owner 提供事实，但都不能取代审计账本。

本专项的产品、数据、UI 与生命周期合同已经通过三轮 Grill Me 与用户冻结。Kiyori 尚未发布，
因此可以直接建立正确的新合同，不保留一套并行的旧“对话详情”实现。历史聊天只按现有事实重建，
缺失信息必须显式标记，禁止伪造。

## 2. 非目标

- 不记录 Provider 服务器内部未返回的模型状态或隐藏推理过程；
- 不记录底层 TCP 分片、无业务意义的心跳、轮询和页面停留时间；
- 不保存 API Key、Authorization、Cookie、密码、私钥、访问令牌和请求签名；
- 不改变 ToolPkg、MCP、插件、Provider wire type 或既有兼容标识；
- 不创建第二套聊天消息、Provider execution、工具执行或日志状态 owner；
- 不调用真实 Provider 或计费接口作为自动验证；
- 本地构建不替代目标设备上的视觉、Back、输入法、旋转、性能与进程恢复验收。

## 3. 冻结产品合同

### 3.1 顶栏与面板

AI 对话页右侧按钮顺序固定为：

```text
浏览栏 | 终端 | 对话详情 | 工作区
```

聊天正文、浏览栏、终端、对话详情和工作区是同一个互斥面板状态。四个入口始终直接可见；
标题单行省略，极窄屏和大字体下继续保留四个入口。再次点击当前面板按钮返回聊天正文。

系统 Back 顺序：

1. 关闭搜索、事件展开、编辑器或弹窗；
2. 从对话详情返回聊天正文；
3. 继续执行 AI 页面原有 Back 或抽屉语义。

在同一次 AI 页面会话内，切换聊天后保持当前详情面板；配置变化与进程状态恢复保留面板；
全新冷启动仍从聊天正文开始。

### 3.2 三视图

对话详情包含：

1. **时间线**：默认视图，一个逻辑事件一行，大型正文按需展开；
2. **对话**：用户输入与 AI 当前输出按行展示，支持选择、复制和允许范围内的编辑；
3. **Raw**：只读结构化 JSONL，提供行号、搜索、选择、复制和软换行。

用户位于底部时实时跟随新事件；用户向上滚动后停止自动跟随，并显示“返回最新事件”和未读数量。
任何新事件都不得抢夺用户正在查看的滚动位置。

### 3.3 编辑

- 用户消息同时提供“编辑消息”和“编辑并重发”；
- 旧“修改记忆”文案改为“编辑消息”；
- AI 输出编辑只作用于当前选中的 variant；
- 用户输入和 AI 可见输出通过新修订事件编辑，原文不可覆盖；
- 编辑后当前聊天投影立即更新，未来模型上下文读取修订后的内容；
- 已经发生的 Provider 请求、实际 Token、等待时间、首包时间和完成时间保持不变；
- 当前上下文估算 Token 按修订后的投影重新计算，并与历史实际用量分别展示；
- 活跃发送或工具执行期间禁止修改会影响本轮上下文的消息；
- 编辑前展示差异和语义提示；编辑并重发继续展示消息、variant、工具和工作区回滚范围。

工具参数、工具结果、异常、调用状态和 Provider 原始事件只读。用户可以添加独立的
“用户注释”；注释作为新的不可覆盖事件追加，后续纠正继续追加新注释，不能伪装或覆盖原始事件。

### 3.4 删除、回滚、variant 与分支

- 消息删除、回滚和 variant 替换只改变当前聊天投影；
- 原始审计事件继续存在，并通过 tombstone 表示删除、回滚、替代和原因；
- 删除整个对话时，消息、审计事件和该对话独占的 payload 才物理删除；
- AI variant 即使从当前投影删除，原正文和删除 tombstone 仍永久保留；当前对话视图显示选中
  variant；
- 切换 variant 记录 `VARIANT_SELECTED` 事件；
- 分支获得截至分支点的自包含审计历史，继承事件记录来源；
- 相同大型 payload 按内容哈希共享，父对话删除不能破坏分支。

### 3.5 完整性状态

用户可见状态固定为：

```text
完整
进行中
部分完整
基础记录
记录中断
内容损坏
来源未验证
密钥不可用
```

详情页顶部始终显示状态；异常状态同时在顶栏“对话详情”图标上显示小型状态标记。

## 4. 数据模型

Room 从版本 21 迁移到 22，新增以下实体。

### 4.1 `conversation_audits`

每条聊天一行：

- `chatId`：主键并外键关联 `chats.id`；
- `schemaVersion`；
- `completenessStatus`；
- `eventCount`；
- `lastSequenceNumber`；
- `chainHeadSha256`；
- `latestSealSequenceNumber`；
- `createdAt`、`updatedAt`；
- `lastFailureCode`；
- `legacyReconstructionLevel`。

### 4.2 `conversation_audit_events`

事件为只追加记录：

- `eventId`：稳定 UUID；
- `chatId` 与单调 `sequenceNumber`；
- `occurredAt`、`recordedAt`；
- `category`、`eventType`、`actor`、`summary`；
- `messageTimestamp`、`variantIndex`、`localExecutionId`、`providerCallId`；
- `parentEventId`；
- `sourceChatId`、`sourceEventId`；
- `previousEventSha256`、`eventSha256`；
- `visibility` 与 `terminalState`。

唯一约束为 `(chatId, sequenceNumber)`。事件正文不直接放入该表。

### 4.3 `conversation_audit_payloads`

内容寻址 payload 元数据：

- `payloadSha256`：明文规范化内容哈希；
- `relativePath`；
- `plainByteCount`、`storedByteCount`；
- `mediaType`、`encoding`、`compression`；
- `encryptionAlgorithm`、`keyAlias`、`nonceBase64`；
- `createdAt`。

真实正文位于应用私有目录的加密文件中。相同明文只保存一份。

### 4.4 `conversation_audit_event_payloads`

事件与 payload 的多对多关系：

- `eventId`；
- `payloadSha256`；
- `label`；
- `ordinal`；
- `role`。

附件、原始输入、转换后输入、异常堆栈、Provider 原始事件和工具结果可分别关联。

### 4.5 `conversation_message_revisions`

消息修订历史：

- `revisionId`；
- `chatId`、`messageTimestamp`、`variantIndex`；
- `revisionNumber`；
- `sender`；
- `contentPayloadSha256`；
- `previousRevisionId`；
- `auditEventId`；
- `source`；
- `createdAt`。

### 4.6 `conversation_message_projections`

每条消息或 variant 的当前投影指针：

- `chatId`、`messageTimestamp`、`variantIndex`；
- `currentRevisionId`；
- `estimatedTokenCount`；
- `updatedAt`。

修改消息时，新修订、审计事件、投影指针和现有 `messages`/`message_variants` 内容更新必须在
同一 Room 事务中完成。

### 4.7 `conversation_audit_seals`

工程防篡改封印：

- `sealId`；
- `chatId`；
- `sequenceNumber`；
- `rootSha256`；
- `signatureAlgorithm`；
- `signatureBase64`；
- `publicKeyBase64`；
- `reason`；
- `createdAt`。

编辑、分支、导入、导出、历史重建和明确完整性边界会封印当前链头；高频文本增量不逐条签名。
该签名用于发现修改，不宣称法律取证能力。

## 5. 加密 payload 与原子性

`ConversationAuditPayloadStore` 使用 Android Keystore 管理的 AES-GCM 密钥。事件链封印使用
独立的 EC P-256 签名密钥。真实凭据在加密前已经由 `ConversationAuditRedactor` 删除。

写入顺序：

1. 规范化并脱敏 payload；
2. 计算内容哈希；
3. 写入同目录临时文件并 `fsync`；
4. 原子替换为内容寻址正式文件；
5. 在 Room 事务中写入 payload 元数据、事件、引用和链头；
6. 事务提交后才允许对应内容进入聊天 UI。

文件先成功、Room 后失败时只会产生无引用 payload。应用启动和审计维护操作扫描无引用文件并
安全删除。Room 永远不能引用尚未原子提交的文件。

存储空间不足时：

- Provider 请求发送前无法持久化完整输入，则不发送请求；
- 活跃回合中关键事件无法持久化，则停止或取消当前执行并标记 `记录中断`；
- 不自动删除旧审计，不静默截断大型正文。

## 6. 事件分类

核心分类：

```text
SESSION
CONTEXT
USER
ASSISTANT
PROVIDER
HOOK
TOOL
ATTACHMENT
WORKSPACE
REVISION
VARIANT
BRANCH
DELETION
ERROR
IMPORT_EXPORT
INTEGRITY
USER_NOTE
```

关键事件类型至少包含：

```text
CHAT_CREATED
SESSION_METADATA_CHANGED
CONTEXT_CONFIGURATION_CHANGED
USER_INPUT_SUBMITTED
USER_INPUT_TRANSFORMED
ATTACHMENT_RESOLVED
WORKSPACE_CONTEXT_ATTACHED
PROMPT_HOOK_STARTED
PROMPT_HOOK_COMPLETED
PROMPT_HOOK_FAILED
PROVIDER_REQUEST_PREPARED
FINAL_PROVIDER_SEMANTIC_REQUEST
PROVIDER_REQUEST_SUBMITTED
PROVIDER_EVENT_RECEIVED
PROVIDER_TEXT_DELTA
PROVIDER_TERMINAL
TOOL_CALL_REQUESTED
TOOL_CALL_STARTED
TOOL_CALL_COMPLETED
TOOL_CALL_FAILED
ASSISTANT_PROJECTION_UPDATED
MESSAGE_REVISED
MESSAGE_DELETED
MESSAGES_ROLLED_BACK
VARIANT_SELECTED
VARIANT_CREATED
BRANCH_CREATED
BRANCH_CREATION_ABORTED
USER_ANNOTATION_ADDED
AUDIT_WRITE_FAILED
AUDIT_EXPORTED
IMPORTED_CONTINUATION
INTEGRITY_SEALED
INTEGRITY_VERIFIED
```

## 7. 写入接线

### 7.1 聊天与消息

- `ChatHistoryManager` 创建真实空聊天时写入 `CHAT_CREATED`；
- `MessageProcessingDelegate.prepareSendUserMessageTurn` 写入原始输入；
- `AIMessageManager.buildUserMessageContent` 在原始文本、代理发送者、回复引用、工作区和附件等
  实质转换节点写入转换事件；
- 当前消息持久化后写入消息投影事件；
- `completeAssistantResponse` 在成功终态前写入最终 AI 投影与实际用量。

### 7.2 Prompt 与上下文

- `EnhancedAIService` 记录最终历史、系统提示词、模型参数和工具 schema；
- `PromptHookRegistry` 在每个实质 Hook 前后记录输入、输出、组件身份、版本、持续时间和异常；
- ToolPkg 消息处理 Hook 使用同一事件合同；
- 只记录 Provider 实际可见、Kiyori 实际构造的上下文。

### 7.3 Provider

- `ProviderRequestContext` 继续提供 chat/message/variant/hop 身份；
- OpenAI Responses 的 `provider_execution_events` 在向 UI 交付前镜像到独立审计账本；
- 非 Responses Provider 由统一流收集边界写入语义事件和文本增量；
- Provider 请求正文以脱敏后的最终规范化内容保存；
- HTTP 状态、脱敏响应和异常阶段写入错误事件；
- 不保存 Authorization、Cookie 和签名请求头。

### 7.4 工具

- `ToolExecutionManager` 在调用请求、开始、完成和失败处写入审计；
- provider-native `call_id`、工具名和参数哈希继续由现有工具账本持有执行唯一性；
- 审计记录独立保存可读参数、结果、错误和关联事件；
- 工具结果必须先持久化审计，随后才能进入下一 Provider hop。

### 7.5 删除、回滚、variant 与分支

- `ChatHistoryManager` 在现有删除事务前写入 tombstone 事件；
- 删除 Provider execution 不影响独立审计；
- 分支复制事件元数据和 payload 引用，并重新建立子对话哈希链；
- 整个聊天删除后执行无引用 payload 回收；
- 导入链保持原签名只读，继续对话创建本地 `IMPORTED_CONTINUATION` 链段。

## 8. 历史聊天重建

迁移 21→22 只创建新表、索引和基础 `conversation_audits` 行，不在数据库打开事务中读取或加密
全部历史正文。

历史重建由 `ConversationAuditLegacyReconstructor` 按需或后台分批完成：

- 有消息和完整 Provider event 的聊天：`部分完整`；
- 只有消息和 variant 的聊天：`基础记录`；
- 缺失字段写入“历史版本未记录”事件；
- 不伪造系统提示词、Hook、完整工具结果和异常；
- 全部重建事件、payload 引用、投影和最终封印在一个 Room 事务中提交；
- 失败时数据库不留下半条重建链，后续可以从未重建状态重新执行；
- 重建过程幂等，并通过 chat ID 判断是否已经存在原生或已完成重建的审计根。

## 9. 导入、导出与备份

### 9.1 完整审计包

使用基于 ZIP 的 `.kiyori-audit`：

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

运行中的对话允许导出一致性快照，manifest 记录 `cutoffEventId` 和
`IN_PROGRESS / FAILED / TERMINAL_UNKNOWN`。导出在同一聊天 Mutex 内封印当前链头、执行完整
验证并确认 seal cursor 等于最后事件序号，再读取稳定快照；不能在 seal 与读取之间插入未封印尾部。

### 9.2 AI 审阅导出

生成分段 Markdown 和内联 payload，保留 event/message/variant/call 关联。导出始终使用完整
一致性快照，不受详情页当前搜索或已加载分页范围影响。

### 9.3 普通聊天归档

`OperitChatArchive` 升级到版本 3，默认包含审计元数据、事件、修订、封印和 payload。
旧 v1/v2 仍可导入并进入历史重建状态。普通 TXT、Markdown 和 HTML 继续作为展示型导出，
不宣称包含完整审计。

### 9.4 隐私配置

- 本机完整审阅：保留必要本地路径和复现环境；
- 外部分享审阅：额外假名化用户名、设备标识、账户标识和私有路径；
- 两种配置都不包含真实凭据；
- 导出报告列出全部脱敏和排除项。

## 10. UI 实现

新增 `ConversationDetailsScreen`，由 `ChatViewModel` 的唯一 `ChatPanelMode` 驱动。
浏览器仍由 Shell 与共享 Browser Runtime 持有，不在 AI 对话页内创建第二套 WebView；
AI 对话页内部状态改为：

```text
CHAT
TERMINAL
DETAILS
WORKSPACE
```

页面结构：

- 顶部完整性状态、事件数、存储大小、当前 Provider/模型和导出入口；
- 时间线支持已加载范围搜索、payload 展开、复制、tombstone 状态和未读事件提示；
- 对话视图按当前路径展示消息，并对允许编辑的行提供“编辑消息”；
- Raw 视图使用只读 JSONL 行、行号、搜索、选择和复制；
- 默认加载最近 100 条事件，更早记录按 sequence 游标分页，新事件按最后事件增量追加；
- 对话视图独立读取完整持久化消息，不复用普通聊天正文的显示窗口；编辑按消息身份而非窗口索引定位；
- 大 payload 只在展开时解密读取；
- 错误和完整性异常使用 Kiyori 语义色，不把普通 Provider warning 全部渲染为错误；
- 搜索、事件展开和编辑状态优先消费 Back；
- 活跃执行期间禁用影响当前上下文的消息编辑。

聊天历史或数据备份设置增加审计存储摘要，显示总大小、对话数量、异常数量和最大占用项；
不提供关闭审计或自动清理开关。

## 11. 文档同步

- `CONTEXT.md`：补充对话审计术语、状态 owner、不可覆盖事件、修订投影和可观察边界；
- `README.md`：补充用户可见的对话详情、导出和隐私说明；
- `docs/doc-src/dev-core/AI_CONVERSATION_AUDIT.md`：长期架构、数据模型、安全和恢复合同；
- 本 TODO：实施阶段、验证证据和设备边界；
- 不修改 `AGENTS.md`，因为本专项没有改变协作与验证规则。

## 12. 串行实施阶段

1. [DONE] 详细方案、门禁与现有状态 owner 对账
2. [DONE] Room 21→22、实体、DAO、迁移和数据库测试源码
3. [DONE] payload 加密、内容寻址、脱敏、哈希链和签名实现
4. [DONE] 审计 repository、历史重建和消息修订事务
5. [DONE] 聊天、上下文、Hook、Provider、工具和错误接线
6. [DONE] 删除、回滚、variant、分支和整聊删除接线
7. [DONE] 顶栏、互斥面板、三视图、编辑、分页和完整性 UI
8. [DONE] v3 聊天归档、完整审计包、AI 审阅导出和导入续接
9. [DONE] 文档、静态反向检查、定向测试、完整 App JVM 与 AndroidTest 编译
10. [DONE] formal/fresh-clone、architecture、差异、Lint baseline 交集、完整 Lint 和 Debug APK 核验
11. [DONE] 精确 staged allowlist、candidate 门禁、提交推送与 local/tracking/remote ref 对账
12. [PENDING] 目标设备视觉、编辑、Back、输入法、旋转、进程恢复和大对话性能验收

### 11.1 最终一致性补强

- 流式正文只有在新快照以前一快照为前缀时写入 `PROVIDER_TEXT_DELTA`；Provider 改写前文时写入
  完整 `PROVIDER_TEXT_REVISION`，不再按长度误判；
- 历史重建和 `.kiyori-audit` 导入均在一个 Room 事务中完成事件、payload 引用、revision、
  projection、续接事件和 seal，后段失败不会留下“调用失败但数据库已部分导入”的状态；
- `createExportSnapshot` 在同一聊天锁中执行 seal、完整验证、seal cursor 检查和快照读取；
- 事件哈希额外绑定 `eventId`、`recordedAt`、payload `mediaType`、`encoding` 和
  `plainByteCount`；验证同时核对根计数/游标、每个 payload 的解密字节数和 SHA-256、seal root
  与目标事件哈希；
- 本机链段的 seal 公钥必须与当前 Android Keystore 签名公钥一致；导入外部链只做数学签名验证，
  并保持 `SOURCE_UNVERIFIED`，本机续接链段仍必须由本机密钥签发；
- 导入 revision 必须绑定对应 `MESSAGE_REVISED` 事件和 payload，projection 必须指向消息链最新
  revision，导入后的聊天气泡/variant 正文必须与当前审计 revision 完全一致；
- payload 缺失、解密或校验失败在详情页显示可复制的完整错误并允许折叠后重试，不再产生未捕获
  UI 协程异常；
- 凭据识别除 Bearer/API Key/Cookie/token/secret/URL 参数外，还覆盖 Basic/Proxy
  Authorization、Set-Cookie 和 PEM private key 块。

## 13. 自动验证矩阵

### Room 与事务

- 21→22 migration；
- 全部表、外键、索引和唯一约束；
- 并发事件单调序列；
- 消息修订与当前投影同事务；
- 删除消息保留审计；
- 删除聊天级联清理事件并回收无引用 payload；
- 分支继承和父聊天删除；
- 历史重建幂等性。

### 安全与完整性

- JSON、URL、headers、工具参数和异常中的凭据脱敏；
- payload AES-GCM 加解密、错误密钥和损坏检测；
- SHA-256 链与 EC 签名验证；
- 原子文件提交和无引用文件清理；
- 导出包路径穿越、重复条目、超大条目和签名错误拒绝；
- 外部分享隐私配置。

### 消息与执行

- 用户输入每个转换节点；
- Hook 前后与异常；
- Provider 文本增量、终态和中断；
- 工具调用开始、完成、失败和下一 hop 顺序；
- 运行中编辑被拒绝；
- 编辑消息与编辑并重发；
- variant、删除、回滚和 tombstone；
- 历史实际 Token 与当前估算分离。

### UI

- 系统级 Browser Home 与 AI Home 互斥，AI Home 内部聊天、终端、对话详情和工作区互斥；
- 四按钮顺序、激活态与标题省略；
- Back 顺序；
- 聊天切换、配置变化和进程状态恢复；
- 三视图、分页、搜索、展开和复制；
- 自动跟随暂停与恢复；
- 完整性状态和异常图标；
- 大 payload 按需加载。

### 本地交付

1. 项目 `.venv` Python 门禁与相关 CI 单元测试；
2. 定向 JVM 与 AndroidTest 编译；
3. `:app:testDebugUnitTest`；
4. 相关 Lint 或完整 `:app:lintDebug`；
5. architecture、formal readiness、fresh clone、Markdown 和 `git diff --check`；
6. 串行 `:app:assembleDebug --no-daemon --console=plain`；
7. APK 包名、版本、唯一 launcher、签名、zipalign、ABI、native 和 DEX 审计；
8. 精确 staged allowlist、敏感内容、异常大文件、嵌套 Git、子模块和远端 ref 审计。

## 14. 风险

- 完整事件会显著增加数据库行数和私有存储，应依靠内容去重、按需解密和分页查询控制成本；
- 流式文本事件过密可能影响响应延迟，事件写入必须批量化但不能改变语义顺序；
- Keystore 密钥在卸载或部分设备安全状态变化后不可恢复，因此正式导出是换机与恢复边界；
- 现有 Provider、Hook 和 ToolPkg 路径分散，漏接任何实际路径都会产生不完整状态，必须通过静态
  注册表和测试矩阵锁定；
- `.kiyori-audit` 导入已经对条目数、单条目和解压总量设硬上限，但 `audit.json` 仍携带
  Base64 payload，包内同时保存独立 payload 文件；后续可在不改变签名合同的独立版本中消除
  这份体积重复；
- 设备上的 Compose 大列表、代码编辑器、输入法和进程恢复仍需真实目标设备验证。

## 15. 当前验收状态

- 产品与技术合同：已冻结；
- 正式开发准备：项目 Python `220/220`、formal readiness、fresh clone、完整 architecture
  `phase=m03` 与 Lint baseline 交集 `retained=5194 / stale=0 / current-only=29` 已通过；
- 代码与数据库实现：已完成本地实现、Room 21→22 迁移源码、定向审计测试、完整 App JVM 和
  AndroidTest Kotlin/Java 编译；
- 自动验证：聚合
  `testDebugUnitTest compileDebugAndroidTestKotlin compileDebugAndroidTestJavaWithJavac lintDebug`
  已通过；App Lint 为 `0 errors / 28 warnings / 1 hint`，29 项均为本轮开始前已有诊断，本功能
  新增诊断为 0，未新增 suppress 或扩大 baseline；
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`295885014` bytes，SHA-256
  `43B3DDBA6E795D515C5DD289CF217992F8B95130E9AB2BEEB7E73C8D0B88E49F`；该 APK 从已推送的
  `f2e78c2bb083ba6bdd54c9db5d3b95badcfefa20` 隔离提交树构建并复制到标准忽略构建路径；包身份
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`、唯一 launcher、Android Debug
  V2 单 signer、16 KB ZIP 对齐、arm64-only、51 个 `.so` 零重复 basename 均通过；
- 正式 native closure 为 51 个 `.so` 加 `assets/operit_shell_exec`，共 52 个 AArch64 ELF，
  153 个 `PT_LOAD` 为 `0x4000 × 151 / 0x10000 × 2`。起始 HEAD 已有、且本轮未修改的共享工作区
  模板 `assets/templates/shared/android-tools/aapt2-arm64-v8a` 不属于该 runtime closure，其 3 个
  `PT_LOAD=0x1000` 作为独立工具链风险保留，不宣称已由本功能解决；
- APK 条目与文本扫描未发现 `.kiyori-audit`、审计 staging、临时 baseline/审计目录、本功能测试类
  或高置信凭据形状；三个 `assets/templates/**/src/{test,androidTest}` 条目是既有项目模板内容；
- 提交与推送：`f2e78c2bb083ba6bdd54c9db5d3b95badcfefa20` 已正常推送到 `origin/main`；
  本地 `main`、tracking `origin/main` 与 `ls-remote origin refs/heads/main` 相等，分歧为 `0/0`；
- 真实 Provider、设备与用户现场验收：未授权或待用户后续执行，保持 `verification_pending`。

## 16. 2026-08-30 诊断中心 UI 与明文 AI 导出优化

状态：`verification_pending`。本增量只调整已存在的对话详情呈现和导出动作，不改变审计事件、
payload、签名链、Room owner、分页边界或 Provider 兼容合同。Kiyori 尚未发布，详情页的
内部 UI 方案可以直接收敛到设置页同款视觉，不保留当前旧表单布局的并行入口。

### 16.1 设计目标与非目标

- 目标：让用户在 5 秒内识别当前对话的完整性、执行规模和 Provider；在三个视图中快速
  搜索、定位、展开和复制诊断事实；导出文件直接可交给外部 AI 阅读。
- 目标：明文 AI 诊断导出必须是 UTF-8 Markdown，包含一致性快照、对话消息、事件、payload
  内容、执行关联和脱敏说明；不使用加密容器，不受当前搜索或分页影响。
- 保留：完整 `.kiyori-audit` 作为需要签名、revision、projection 和二进制 payload 的本机
  复现包，作为独立的高级动作，不与明文诊断文件混淆。
- 非目标：不提供第三套导出格式、不把原始未脱敏凭据重新放回导出、不改变历史审计事实、
  不引入第二个 ViewModel/Repository 状态 owner。

### 16.2 UI 方案

1. 详情页使用 `KiyoriSettingsTheme` 的页面底色、卡片色、分隔线和蓝色强调色；内容按设置
   页分组卡片组织，圆角不超过设置页既有尺度，避免大面积灰色实心块。
2. 顶部压缩为两层：第一层是标题、完整性状态、明文诊断导出和更多动作；第二层是事件、
   消息、payload、Provider/模型等关键指标。长链头和路径只在可展开的元数据区显示。
3. 三个 Tab 保留“时间线 / 对话 / Raw”语义，但 Tab 下方统一使用紧凑搜索栏：搜索图标、
   清除按钮、匹配数量和“全部展开/全部收起”动作在同一行，搜索范围始终是完整已加载数据。
4. 时间线事件、对话消息和 Raw 记录都具备独立展开状态。折叠态只显示可扫描摘要；展开态
   显示关联 ID、状态、时间、payload 元数据与正文。大 payload 仍按需读取，错误可见且可重试。
5. 对话视图增加搜索、角色筛选提示、消息序号和 Token 元数据；编辑动作保留，但只在消息
   展开后出现，避免把诊断列表误做成编辑器。
6. Raw 视图保留行号和选择复制，折叠态展示一行 JSON 摘要，展开态展示格式化字段；搜索命中
   高亮由条目背景和匹配计数表达，不改变 JSON 内容。
7. Back 顺序保持既有合同：先关闭导出/注释弹窗，再关闭搜索或展开内容，最后退出详情面板。

### 16.3 导出动作

- 默认动作命名为“导出 AI 诊断（明文 Markdown）”，产物扩展名为 `.md`，文件名明确包含
  `ai-diagnostics`；内容采用完整一致性快照并内联已脱敏 UTF-8 payload，便于直接上传给 AI。
- “导出完整审计包（签名 ZIP）”作为明确的次级动作，扩展名保持 `.kiyori-audit`，用于本机
  复现和导入；UI 必须标注它是结构化容器，不宣称可直接阅读。
- 两个动作都在同一聊天 Mutex 内封印并验证链头；每次按用户选择只生成一个目标文件，导出完成
  后只追加一个 `AUDIT_EXPORTED` 事件，并在结果提示中给出所选产物的实际文件名。

### 16.4 验收清单

- 三个 Tab 都存在搜索框，输入、清除、Back 和匹配计数行为一致；
- 三个 Tab 的每一条内容都可单独展开/收起，“全部展开/全部收起”不改变数据顺序；
- 时间线分页、实时新事件提示、payload 按需读取和错误重试继续有效；
- 明文导出可用文本编辑器读取，不包含加密标记或 Base64-only 正文；完整包仍可导入且签名
  验证合同不变；
- 屏幕窄宽、大字体、深色主题和空数据状态不发生文字重叠或横向溢出；
- 定向 Kotlin 编译/测试、`git diff --check`、正式门禁和串行 Debug 构建通过；提交前完成
  staged allowlist、敏感内容、构建产物和远端 `main` 对账；目标设备视觉和触摸验收仍单独
  记录为 `verification_pending`。
