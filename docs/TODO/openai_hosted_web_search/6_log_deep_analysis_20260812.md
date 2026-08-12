---
status: analysis_complete
implementation: documentation_only
source_changes: none
log_date: 2026-08-12
log_timezone: Asia/Shanghai
log_sha256: E4B56B736AAC7B8E54B7CAB830A0A760BA788ECF788A6E0050FA738F6EF10DF7
---

# 2026-08-12 Kiyori 日志深度分析

## 1. 分析边界与结论摘要

本报告分析：

```text
C:\Users\admin\AppData\Roaming\dragfile_file\kiyori_log_20260812_232122.txt
```

本轮只读取日志、相关源码、测试和专项文档，并把后续修复所需的事实、问题分级、验证条件和
修改候选落实为文档。没有修改业务源码、配置实现、测试实现、APK、设备状态或远端 Git。

最重要的结论：

1. 本次应用启动和聊天收尾没有出现 Android 系统 ANR、Java 崩溃、Fatal Exception 或 native
   tombstone。日志中的 510ms 主线程记录是监控器的性能警告，不是系统已经发生 ANR。
2. DeepSeek 标题请求正常完成，基础 DNS、TLS、HTTP 和模型请求链并未整体失效。
3. 主聊天的 OpenAI Responses 请求已经完成 DNS、TLS、请求头和 16102 bytes 请求体发送；Android
   客户端在收到任何 HTTP response headers 之前，与 Pixel relay 的 HTTP/1.1 连接被中止。
4. 客户端把本次请求标记为 `OpenAIResponsesSubmissionUnknownException`，没有自动重复提交；
   这是当前 at-most-once 约束下的正确安全收口，不能仅凭该日志加入整轮重试、自动切换 endpoint
   或自动改写请求后再次提交。
5. 当前日志不足以把连接中止归因到 relay 的具体实现、请求字段、代理策略、连接池复用、Android
   TLS 栈或 OEM 网络策略。后续必须增加传输诊断并用受控的 relay/设备矩阵定位。
6. 日志存在两个明确的隐私问题：API Key 前后缀/末尾指纹被记录；完整请求正文包含系统提示词、
   用户消息、工具描述和包能力列表。
7. 同一个 `localExecutionId` 的同一个异常被多个流观察层重复输出完整堆栈，噪声远高于一次主
   错误所需的诊断量。现有 `StreamFailureOwnership` 已定义主错误所有权，后续应把这个合同落实
   到日志聚合，而不是删除主错误或吞掉异常。
8. `AIForegroundService` 把服务销毁时的正常协程取消记录为 ERROR；`UIHierarchyManager` 的
   绑定失败日志还存在状态语义不准确和清理证据不足的问题。
9. TextSegmenter 的异步预热耗时 4715ms，当前日志没有证明它阻塞首屏，但它与启动期大量后台
   初始化存在资源竞争可能。应先补充阶段和线程观测，再决定是否调整预热时机。

## 2. 日志基线

| 项目 | 已确认值 |
| --- | --- |
| 文件 | `kiyori_log_20260812_232122.txt` |
| 文件大小 | 125135 bytes |
| 物理行数 | 1177 |
| 日志头声明总条数 | 1172 |
| SHA-256 | `E4B56B736AAC7B8E54B7CAB830A0A760BA788ECF788A6E0050FA738F6EF10DF7` |
| 主体时间范围 | 2026-08-12 23:20:59.770 至 23:21:15.325 |
| 时区 | `+08:00`，与本次任务的 `Asia/Shanghai` 一致 |
| 业务结果 | 用户消息已保存；AI 回合以 `provider_failure` 终止 |
| 设备/网络现场 | Pixel relay；本报告不保存完整 endpoint、Key、Authorization 或账号信息 |

“物理行数”和“日志头声明总条数”不一致，暂时只能视为导出格式的计数差异。它不会改变下面
基于带时间戳事件和堆栈的判断，但后续日志导出器应明确区分事件条数、物理行数和多行异常堆栈行数。

## 3. 事件时间线

### 3.1 启动与工具包加载

| 时间 | 事件 | 证据等级 | 说明 |
| --- | --- | --- | --- |
| 23:20:59.770–23:20:59.817 | 首屏必需初始化完成 | 已证实 | 总耗时 48ms |
| 23:21:00.445–23:21:00.475 | 首帧后完整初始化提交 | 已证实 | 主要工作放入后台任务 |
| 23:21:00.483–23:21:00.487 | UIHierarchyManager 两次 `bindService()` 返回 false | 已证实 | 独立问题，未中断普通聊天启动 |
| 23:21:00.915 | `openai_web_search` ToolPkg 注册成功 | 已证实 | 不能由此推断真实搜索请求已成功 |
| 23:21:01.558 | ToolPkg 加载完成 | 已证实 | `available=52`、`containers=12`、`subpackages=9`、`errors=0` |
| 23:21:01.541–23:21:01.569 | Shizuku 检查成功 | 已证实 | 与本次 Responses 传输失败无直接关系 |
| 23:21:05.170–23:21:05.172 | TextSegmenter 异步预热完成 | 已证实 | 耗时 4715–4716ms |

### 3.2 标题请求

标题请求使用 DeepSeek Chat Completions，`stream=false`，最终得到 HTTP 200 和标题
“评估OpenAI联网搜索工具”。该请求经历了 `responseHeadersStart`、响应体读取和正常连接释放。

因此：

- DNS、TLS、OkHttp 基础执行器和 DeepSeek 连接路径在本次会话可用
- 标题生成不是用户看到的主聊天失败原因
- 标题请求的完整提示词也被当前请求体日志暴露，属于同一套隐私问题

### 3.3 主聊天 Responses 请求

主聊天请求的关键状态如下：

```text
provider = OPENAI_RESPONSES
model = gpt-5.6-sol
stream = true
execution persistence = RESPONSES_AT_MOST_ONCE
endpoint label = Pixel relay
request body = 16102 bytes
```

传输事件顺序：

1. DNS 解析成功。
2. TLS 1.3 建立成功，密码套件为 `TLS_AES_128_GCM_SHA256`。
3. 实际协商协议为 HTTP/1.1。
4. 请求体发送结束，`requestBodyEnd` 记录 16102 bytes。
5. 没有出现 `responseHeadersStart`。
6. 约 3638ms 时出现 `connectionReleased` 和 `SocketException: Software caused connection abort`。
7. 没有 HTTP 状态码、响应头、响应体或任何 Responses stream chunk。

这条链只能证明“客户端在等待响应头时遇到连接中止”。它不能证明：

- relay 一定没有把请求交给上游模型
- 模型一定没有开始执行 hosted web search
- 服务端一定没有产生 response ID
- 连接一定由 relay 主动关闭
- 请求一定因某一个 JSON 字段被拒绝

### 3.4 错误收尾

最终日志包含：

```text
OpenAIResponsesSubmissionUnknownException
Responses submission state is unknown for local execution 0f8fc34e-d9ab-43da-a817-0be4f82f2ee1

turn terminal:
outcome=provider_failure
cancellationSource=null
chunks=0
visibleChars=0
provider=OPENAI_RESPONSES/pixel-gpt
model=gpt-5.6-sol
providerRequestContextPresent=true
```

可确认：

- 不是用户主动取消
- 没有收到模型文本
- 没有收到 tool call chunk
- 没有自动重复 POST
- 主消息 owner 最终写入了失败终态

## 4. 问题清单与优化点

严重度说明：

- `P1`：用户可见失败、凭据/私聊泄露或提交状态安全边界，需要优先处理
- `P2`：会明显降低诊断质量、误导状态或增加运行时风险
- `P3`：性能观测和启动资源优化点，必须先用额外证据确认收益

### 4.1 P1：Responses response-header 前连接中止

**编号：`NET-01`**

**已证实事实**

- 请求体已发送完成
- 没有 `responseHeadersStart`
- 失败类型为 `SocketException`
- 实际连接是 HTTP/1.1
- 失败发生在 OkHttp/Conscrypt 读取 response headers 的路径

**当前影响**

- 主聊天没有任何可见内容
- 本地回合进入 `provider_failure`
- 请求的服务端提交状态无法确认
- 用户无法从当前日志判断是 relay、请求合同还是 Android 网络栈问题

**根因状态**

尚未锁定。以下都只能作为待验证假设：

- relay 在 HTTP/1.1 下对请求体、响应生成或 SSE/stream 处理存在连接生命周期问题
- relay 对本次完整请求的工具、思考强度、系统提示词或请求大小组合处理失败
- relay 或中间网络设备在上游等待期间关闭下游连接
- OkHttp 连接池复用了失效连接，或 relay 对复用连接的处理不兼容
- Pixel 的 Android/OEM 网络栈与宿主外测试环境存在行为差异

**不能采取的处理**

- 不能在异常后再次提交整轮 POST
- 不能根据异常自动更换 endpoint、模型、Key、搜索后端或协议模式
- 不能静默删字段、改 reasoning 或改成另一种请求后声称原请求已修复

### 4.2 P1：传输诊断不足，无法区分连接阶段

**编号：`NET-02`**

`AIServiceFactory.kt` 已有 OkHttp `EventListener`，但当前日志只记录了阶段名称、协议、TLS 和
请求体大小。没有形成可持久检索的传输快照，也没有记录：

- 是否曾进入 response headers 阶段
- 失败时的 `IOException` 分类和底层 close 线索
- 响应 ID、request ID、trace ID 的脱敏关联
- response header 是否到达但未成功解析
- 连接是否来自连接池复用
- 本次请求的不可逆请求合同指纹

后续应补充结构化 diagnostic code 和阶段状态，不能通过打印完整 headers 或完整请求体来解决。

### 4.3 P1：API Key 前后缀泄露

**编号：`SEC-01`**

当前 `ApiKeyProvider.kt` 会记录 Key 的前四位、后四位或固定凭据类型标记加末尾四位。本次日志中
已经出现可关联的部分 Key 指纹。虽然不是完整 Key，但它会暴露凭据类型、末尾指纹和跨日志关联
信息，不符合专项文档已经冻结的“日志不包含 Key 前后缀”安全目标。

后续应完全移除原始前缀和后缀输出。若确实需要区分配置或 Key 轮换，只允许使用现有兼容性合同
认可的不可逆、非凭据化关联标识，并明确生命周期和碰撞边界；不能从日志标识反推出 Key。

### 4.4 P1：完整请求正文进入本地日志

**编号：`SEC-02`**

`OpenAIProvider.kt` 当前只省略 `tools` 数组，并只对图片数据做清理，然后把请求 JSON 以大文本
形式记录。此次日志中可以看到：

- Kiyori developer prompt
- 用户原始消息
- 已安装和可用 ToolPkg 列表
- 工具描述、权限和系统能力信息
- 标题请求的系统提示词和用户消息

这会让普通诊断日志携带私聊内容、工具合同、能力列表和潜在网页/文件上下文。日志一旦被用户
分享、备份或上传，就不能被视为安全的最小诊断材料。

后续应建立统一请求日志合同：

- 默认不记录完整 prompt、完整 message、完整 tools 或完整 request body
- 记录请求大小、角色计数、工具数、协议、模型和不可逆的短期请求合同摘要
- 用户内容只在明确授权的临时 debug 模式下，以受限长度和脱敏形式出现
- Authorization、Cookie、完整自定义 headers、Key、私密路径和 URL 查询参数永不输出
- 对所有 provider 共用，不只修 OpenAI Responses

### 4.5 P2：同一异常被多个观察器重复打印完整堆栈

**编号：`ERR-01`**

同一个 `localExecutionId` 和同一个 `OpenAIResponsesSubmissionUnknownException` 被
`StreamFramework`、`EnhancedAIService`、`ChatServiceCore`、`NativeMarkdownBlockSplitBy`、
`RevisableTextStream`、`MessageProcessingDelegate`、`ChatArea` 等层重复输出。

这不是多个独立网络失败，而是一个失败沿消息流、修订流、首包观察、渲染和 UI 边界传播时重复
记录。现有 `StreamFailureOwnership.kt` 已规定主消息流拥有失败状态，次级观察器只能记录并结束；
当前日志呈现说明“异常所有权”和“日志所有权”还没有统一。

后续应：

- 以 `localExecutionId`、diagnostic code 和 cause fingerprint 做一次回合级聚合
- 主 owner 记录一次完整 cause chain
- 次级观察器只记录 observer name、阶段和是否已被主 owner 接收
- 同一回合不重复打印完整 stack trace
- 最终 `turn terminal` 记录唯一诊断码、传输阶段、请求关联标识和观察器计数
- 仍保留原异常对象交给主消息 owner，不能通过吞异常来降噪

### 4.6 P2：服务生命周期取消被记录为 ERROR

**编号：`ERR-02`**

`AIForegroundService.kt` 的两个 Flow 观察器使用 `catch (e: Exception)`。服务销毁时
`serviceScope` 被取消，日志却记录：

```text
监听后台保活设置失败: Job was cancelled
监听运行时任务视图隐藏设置失败: Job was cancelled
```

这是正常生命周期取消，不是后台保活或任务视图功能失败。当前 ERROR 会污染错误检索和现场判断。

后续应在观察器中单独识别 `CancellationException`，让取消保持取消语义并以 debug 或不记录方式
处理；只有非取消异常才记录 ERROR 和完整 cause。真实 Flow 异常不能被吞掉。

### 4.7 P2：UIHierarchyManager 绑定状态和清理证据不准确

**编号：`UI-01`**

日志显示：

```text
服务解析成功
ApplicationContext 绑定结果: false
原始 Context 绑定结果: false
bindService返回false，绑定失败
bindToService 成功完成
```

当前 `bindToService()` 在得到 `false` 结果后仍会输出“成功完成”，这会使搜索日志时误判绑定
已经成功。代码还同时尝试 ApplicationContext 和原始 Context，并在超时清理阶段对两个 Context
分别 `unbindService()`，但没有持有“哪一个 Context 真正建立了绑定”的明确状态。

这至少包含三个待修复合同：

- 返回值、内部 `_isBound` 和成功日志必须表达同一状态
- 绑定应记录单一的绑定 owner 和精确清理状态
- 失败诊断应包含 provider 是否 enabled、service 是否 exported、permission/flags、前后台状态、
  Android/OEM 版本和实际 Context 类型

本次日志不能证明外部 provider 本身一定不可用，也不能证明后台启动限制就是原因。

### 4.8 P2：绑定失败原因过于笼统

**编号：`UI-02`**

当前错误文本把权限问题、后台启动限制和其他未分类原因合并到一句话。服务已经成功解析，因此
后续诊断至少需要在不输出私密内容的前提下记录：

- `ServiceInfo.enabled`
- `ServiceInfo.exported`
- service permission
- resolve flags
- package enabled state
- 调用时的 application/activity/service Context 类型
- 当前进程是否处于前台
- `SecurityException`、返回 false、超时、连接回调失败分别属于哪一类

如果在不同 Android/OEM 上存在不同失败原因，状态页和日志应显示结构化分类，而不是让用户根据
“可能是权限问题或后台启动限制”自行猜测。

### 4.9 P3：510ms 主线程警告缺少上下文

**编号：`PERF-01`**

`AnrMonitor.kt` 的阈值为：

```text
WARNING_THRESHOLD_MS = 500
ANR_THRESHOLD_MS = 1000
SAMPLING_INTERVAL_MS = 100
```

本次只出现约 510ms 警告，因此它说明一次超过 500ms 的主线程心跳延迟，不代表 Android 系统
已经判定 ANR。

当前日志缺少：

- 启动阶段或用户操作阶段
- 连续超时采样次数
- 主线程消息/trace 上下文
- 警告发生时的应用生命周期状态
- 与 ToolPkg 加载、数据库、图片池、TextSegmenter 等后台任务的时间关联

此外，当前源码搜索没有发现 `reportSlowResponse()` 的实际生产调用点。需要确认它是保留接口、
未接线接口还是测试专用接口，再决定删除或接入唯一事件 owner，不能让两个统计路径长期漂移。

### 4.10 P3：TextSegmenter 异步预热耗时 4715ms

**编号：`PERF-02`**

TextSegmenter 通过 `applicationScope` 的后台任务执行 Jieba 词典加载，日志报告 4715–4716ms。
因此没有直接证据证明它阻塞了首屏；首屏必需初始化在 48ms 完成，预热发生在首帧后。

但异步不等于没有代价。启动期它可能与 ToolPkg、数据库、Shizuku、角色卡、图片池和其他任务
竞争 CPU、磁盘或内存带宽。后续需要记录：

- dispatcher、线程名和任务排队等待时间
- 冷启动/热启动
- 词典加载大小和初始化次数
- 首次真实 memory search 的初始化耗时
- 预热期间主线程警告是否重复出现

在证据不足前不应直接移动、删除或复制一套新的分词器初始化流程。

### 4.11 P2：日志导出计数不一致

**编号：`LOG-01`**

文件物理行数为 1177，头部声明总条数为 1172。由于异常堆栈占用多行，当前导出格式没有明确
说明“条数”究竟统计事件还是物理行。它不会改变本次主结论，但会影响自动解析、错误计数和跨版本
对比。

后续应给日志记录增加稳定事件 ID、父异常 ID、事件类型和导出统计口径；多行 stack trace 应作为
同一事件的结构化字段，而不是依靠物理行拼接。

## 5. 已确认的正确行为

以下行为不应被本次日志误判为待修复根因：

### 5.1 at-most-once 提交状态收口

Responses 请求在进入 `call.execute()` 前标记 `responsesSubmissionStarted=true`。传输异常发生
后，`OpenAIResponsesSubmissionUnknownException` 记录本地执行 ID，并阻止整轮重复 POST。

对于“请求可能已经到达服务端，但客户端没有读到响应头”的窗口，这是正确的安全行为。后续优化
应提高可观测性和恢复提示，不能通过自动重复提交制造重复模型执行或重复费用。

### 5.2 标题请求和主请求使用不同 provider

DeepSeek 标题请求成功，不能被误读成主请求也已经成功；主请求使用独立的 OpenAI Responses
配置，二者的成功/失败状态应分别记录。

### 5.3 ToolPkg 注册成功不等于 hosted search 成功

`openai_web_search` 注册成功只证明本地插件加载和注册完成。它不能替代一次真实的 gateway
请求、Responses `web_search_call`、citation、source 或 usage 验证。

## 6. 未决问题与证据缺口

| 编号 | 未决问题 | 最短证据 |
| --- | --- | --- |
| `Q1` | Pixel relay 是否在收到请求后主动关闭下游连接 | relay 端 request/trace 日志和连接关闭原因 |
| `Q2` | 失败是否只发生在 HTTP/1.1 | 同一 endpoint 的协议对照或服务端协议日志 |
| `Q3` | 完整请求中的哪个字段组合触发连接中止 | 固定 Key、固定模型、单变量请求矩阵 |
| `Q4` | 共享连接池是否复用了失效连接 | 新鲜 client/禁用复用对照，仅作诊断实验，不直接改变产品行为 |
| `Q5` | Android/OEM 网络栈是否参与关闭 | 同一 APK 在另一设备和同一设备的最小请求对照 |
| `Q6` | 服务 provider 是否真正启动或被系统限制 | ServiceInfo、package state、系统事件和 provider 自身日志 |
| `Q7` | TextSegmenter 是否造成启动期资源竞争 | Perfetto/系统 trace、线程时间线和重复冷启动样本 |
| `Q8` | `reportSlowResponse()` 是否仍有兼容调用方 | 全仓生产/测试引用审计 |

## 7. 本报告的交付边界

本报告完成的是证据整理和后续修复输入，不是业务修复完成证明。

当前状态：

- 业务源码：未修改
- 配置实现：未修改
- 现有用户 dirty changes：未触碰
- 设备/ADB/模拟器：未操作
- relay 现场复测：未在本轮执行
- Git commit/push：未执行
- 下一轮修复入口：[后续修复计划](7_log_followup_fix_plan_20260812.md)
