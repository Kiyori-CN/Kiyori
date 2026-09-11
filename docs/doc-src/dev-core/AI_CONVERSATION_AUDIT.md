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

聊天 DAO 和审计仓储必须持有同一个 `AppDatabase` 实例，才能共享 Room 事务上下文。
`AppDatabase.getDatabase` 与 `ChatHistoryManager.getInstance` 在单例初始化锁内再次检查
已发布实例，确保并发首次访问不会创建第二个数据库或聊天仓储 owner。只检查锁外的
`@Volatile` 引用不足以满足该约束；不同 Room 实例对同一文件的嵌套写入可能触发 `SQLITE_BUSY`。

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
- **原始事件**（英文 Raw events）：只读 JSONL 行、行号、搜索、匹配计数、逐条或全部展开、选择和复制，展开态显示
  格式化 JSON。

默认只加载最近 100 条事件。更早事件通过 sequence 游标分页加载；新事件由最后事件观察器触发
增量查询，详情关闭后停止分页观察并释放列表。加载旧页不会把用户拉回底部。大 payload 在展开或明确全文搜索时于 IO 线程解密。头部显示完整性、总事件
数、已加载数、链头、当前 Provider/模型和本聊天审计存储占用。对话视图独立读取完整持久化消息，
不复用普通聊天正文的显示窗口；编辑按消息身份定位，避免长对话窗口裁剪导致改错行。

三个视图共用紧凑的无边框搜索栏，Back 优先关闭注释/导出弹窗、展开内容、编辑弹窗和当前
搜索，最后关闭详情面板。搜索只改变当前视图，不改变导出快照范围。

详情的临时状态以聊天 ID 隔离，三个标签页分别保留搜索与列表位置。宿主隐藏时，详情与子视图不
消费系统 Back，也不展示遗留弹窗。分页结果绑定聊天与加载代次，旧请求不能清除新请求的加载态；
加载失败保留明确错误及手动重载入口。未搜索时的事件计数与原始事件页的匹配数明确指已加载事件，没有匹配结果时仍
能加载更早事件。时间线输入关键词后遍历全聊天事件和 UTF-8 payload：按 sequence 每页 100 条，
空格分词按 AND 匹配，大小写不敏感，正则符号按字面量处理，展示命中片段与已检查数量。
搜索有防抖、取消和错误状态；每批至少找到 100 个命中后可继续，不保留全历史明文索引。
结果固定在开始搜索时的历史范围，新记录可清空后重新搜索；原始事件页仍只查已加载 JSON 元数据。

诊断备注、修订和导出等待实际结果，操作中禁用重复提交；失败保留输入及可见错误。备注的事件与
seal 在同一数据库事务内提交，不发送给 AI；封印失败不会留下可被重试重复追加的备注。修订保存原消息
快照，在仓储锁内比较原文，拒绝覆盖其他入口刚提交的修订。payload 只由可见展开行保留，折叠或
离屏取消读取并释放内容；失败提供显式重新加载按钮，取消不作为错误堆栈显示。
超长消息与文本 payload 每页最多约 12,000 个 UTF-16 字符，页边界保留完整代理对；分页只约束
界面排版，不截断审计、搜索或导出。已保存的基础回答与选中版本分别进入诊断导出的历史和当前对话。

导出使用同目录临时文件，完整写入后发布，不覆盖已存在结果；失败清理本次临时文件。如果文件已
发布但追加导出事件或封印失败，错误明确提供已生成的文件位置，不把该状态当作完全未生成文件。
页面离开产生的取消与已经发布的本地文件之间不存在跨系统事务，不承诺撤销已完成的文件发布。

导出完成事件保留写入时的完整性状态，不能以较早的导出快照覆盖正在执行的回合状态。
记录完整性与回答成功是两件事；头部最近异常码和时间线终态共同描述失败。应用运行日志保持独立，
覆盖启动、代理、终端服务等跨对话事实，清空它不删除对话审计。

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
按既有可识别规则替换；脱敏器不能识别任意无标签秘密，分享前仍需检查正文。

“导出完整审计包（签名 ZIP）”是独立的高级动作，扩展名保持 `.kiyori-audit`，保留事件链、
修订、投影、封印和二进制 payload，用于本机复现和导入。两个动作都在聊天 Mutex 内创建并验证
同一个一致性快照，导出结果不会受当前视图搜索或分页影响。

完整包的 `chat.json` 与可读时间线在导出时重新脱敏普通聊天投影，覆盖标题、当前正文和历史
variant；原始事件、已脱敏 payload 字节及签名不被改写。导出文件名的聊天标识使用 SHA-256
摘要前缀，避免导入 ID 中的路径分隔符影响目标位置。

Markdown 逐段写入临时文件，避免另建整个文档及 UTF-8 副本。当前对话按选中 variant 投影；
保留历史 variant、修订关联、当前 revision 关联和可读 UTC 时间。供应商 usage v2 同时导出请求
覆盖率、缓存指标覆盖率、输入/输出/推理和缓存读写；未返回数据明确为 `not_reported`。
Markdown 不内嵌二进制附件，相关位置明确说明，实际字节由完整审计包保存。

同一 payload SHA-256 在一次 Markdown 导出中只展开一次正文；后续事件或修订保留原哈希、
字节数及指向本文件首次正文的链接。这样避免未改变的 Hook 输入/输出和后续发送快照反复展开，
同时保留每个事件、修订与机器可读引用。诊断仅展开 UTF-8 payload：每项最多 64 KiB，合计最多
8 MiB，错误类别和失败终态的正文优先分配预算。超出预算或单项上限时保留哈希、字节数、引用与
省略说明，不对截断的 JSON 假装进行完整解析。当前消息和历史 variant 在脱敏后最多保留首尾各
32 Ki 个 UTF-16 字符，中间省略数量明确标注。上述上限约束阅读投影，不是整个文件的大小上限。
首次展开按原规则脱敏并选择安全代码围栏；完整审计包继续按既有 `payloads/<sha>` 路径保存全部
原 payload，存储与封印不变。诊断快照中省略的正文不能进入完整归档序列化。

快照与校验按聊天和 sequence cutoff 批量读取引用及 payload 元数据；校验时每个唯一正文只
解密/解压一次，导出复用已验证的字节。诊断省略展示的正文仍执行完整性校验，但不在快照中保留
字节数组。JSONL 逐事件写入，不额外拼接整条时间线。payload 脱敏、压缩、加密和 fsync 以及最终
语义请求审计在 IO dispatcher 执行；不以异步丢弃审计的方式减少 UI 等待。
签名 ZIP 本身也不加密；外部审阅版经额外脱敏的文本不能用于独立重算原始事件签名。

内置 `linux_ssh` / `windows_control` 将长输出写入受管 `cleanOnExit` 日志时，工具审计识别其明确的
`output_saved_to` / `outputSavedTo` 字段，在事件中保存脱敏的 `saved_tool_output` 正文。
只接受受管目录内、符合工具输出命名的 `.log` 文件，每份读取上限 8 MiB；路径越界、文件缺失、
权限、编码或限额问题产生 `TOOL_OUTPUT_ATTACHMENT_UNAVAILABLE` 与 `PARTIAL`，不改变工具已经
执行的结果，不重新执行远端命令。快照随后随加密审计和导出保存，不依赖临时文件寿命。
普通终端的 4 Mi 字符采集上限、远端进程输出缓冲和 tmux 屏幕范围仍属于上游可观察性边界；
已被上游截断、未返回或未读取的字节不能由审计重建，必须保留相应截断与未知状态。

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

单条和历史选择器分组删除在删除事务中记录事件与 revision 的候选 payload 哈希，删除后在
`payloadLifecycleMutex` 内重新检查全局引用，只回收本次涉及且已无引用的文件。设置页逐条
批量删除复用同一路径，不再每条扫描其他聊天的全部 payload。共享哈希在最后一个引用消失
前始终保留；候选快照与级联删除处于同一 Room 事务，避免并发写入产生遗漏。

文件清理由 IO 调度器执行，候选查询按 128 个哈希分块以兼容 SQLite 参数上限；文件删除后
每批一次事务提交元数据，避免逐文件独立提交。全库清理入口仍供其他删除路径使用，引用
检查采用已有索引上的 `NOT EXISTS`。数据库删除成功后的清理失败仍是部分完成错误，不得
显示成可重复删除的普通失败；文件已删但元数据尚存时，后续无引用清理可以继续处理。

## 验证边界

当前实现已经通过 JVM 单元测试、Room/迁移 AndroidTest 编译、完整 App 测试、Lint、项目
Python/architecture/formal/fresh-clone 门禁与 Debug APK 静态审计。该证据证明本地源码、
数据库合同和构建产物闭合，但不等于真实 Provider 或目标设备验收。

本地自动验证不能替代目标设备上的以下验收：

- 顶栏四按钮在窄屏、大字体和横屏下的触控与省略；
- 三视图滚动、复制、输入法和 Back；
- 旋转、进程重建、Keystore 状态变化和大对话分页性能；
- 实际 Provider、ToolPkg 和工具的现场完整链。
