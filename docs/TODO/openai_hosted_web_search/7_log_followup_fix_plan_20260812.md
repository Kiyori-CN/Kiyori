---
status: local_implementation_complete
implementation: W1_complete_W2_complete_W3_complete_W4_complete_W5_complete_W6_observation_complete
source_changes: W1_transport_diagnostics_W2_log_privacy_W3_failure_ownership_W4_cancellation_W5_binding_contract_W6_startup_observation
based_on: 6_log_deep_analysis_20260812.md
plan_date: 2026-08-12
local_verification_date: 2026-08-13
final_local_verification_observed_at: 2026-08-13T03:56:14+08:00
remote_relay_verification: pending
device_verification: pending
user_acceptance: pending
---
# 2026-08-12 日志驱动后续修复计划

## 1. 计划目标

本计划把
[日志深度分析](6_log_deep_analysis_20260812.md)
中的问题转化为可以逐项实现、测试和现场验收的修复工作包。

本计划不是本轮业务源码变更授权，也不把尚未锁定的 relay 根因写成已完成事实。后续实施必须
继续保留当前 `main` 工作树中的用户改动，并在每个工作包完成后独立审阅差异和验证证据。

目标：

- 能准确知道 Responses 请求失败发生在请求、响应头、响应体还是 stream 事件阶段
- 在不重复提交未知状态请求的前提下，提供足够的 relay/设备定位证据
- 移除 API Key 和完整 prompt 的普通日志暴露
- 让同一个回合只有一个完整错误 owner，降低重复堆栈噪声
- 把正常协程取消与真实业务异常分开
- 修正 UIHierarchyManager 的绑定状态、清理和诊断合同
- 用可重复的启动样本判断 510ms 主线程警告和 4715ms 分词预热是否需要行为调整

非目标：

- 不增加自动重复 POST
- 不自动切换 endpoint、模型、Key、搜索后端或协议模式
- 不把日志诊断改成上传源代码、完整请求或完整 headers
- 不在没有设备证据时宣称 Pixel/OEM/relay 根因已修复
- 不重新设计整个网络层、ToolPkg 系统或日志系统
- 不借机升级无关依赖、批量格式化或清理其他 dirty changes

## 2. 实施顺序与门禁

建议按以下顺序推进：

1. `W0`：建立本轮修复分支边界、现状快照和敏感信息禁止清单
2. `W1`：完成网络传输诊断合同和 response-header abort fixture
3. `W2`：完成请求日志脱敏合同以及 API Key 日志清理
4. `W3`：完成错误所有权与回合级日志去重
5. `W4`：完成 AIForegroundService 取消语义修复
6. `W5`：完成 UIHierarchyManager 绑定状态与失败分类修复
7. `W6`：完成 ANR/TextSegmenter 启动性能观测，再决定是否调整时机
8. `W7`：串行执行专项测试、正式准备度检查、`git diff --check` 和 Debug APK 构建
9. `W8`：在用户明确授权后执行 relay/设备现场矩阵，并把现场证据追加到本专项文档

每个工作包都必须满足“代码差异、测试、日志证据和未验证边界”四项闭环；任何一项缺失都只能
报告为部分完成或待验证。

## 2.1 当前实施快照

本计划已经从“待实施”进入“本地实现完成、现场验证待执行”状态。W1–W6 的实现和定向验证均
已写入当前 dirty worktree，基线仍为 `main` / `3528f7d9`，本轮不提交、不推送。

已完成的本地工作包：

- `[DONE] W1`：Responses 传输阶段诊断、请求体字节数、协议/TLS/连接复用和未知提交状态快照；
  保持 at-most-once，不新增第二个 POST
- `[DONE] W2`：API Key 前后缀、完整 prompt、完整 request/response 原文和 provider 敏感日志收口；
  统一使用结构化摘要与受控错误摘要
- `[DONE] W3`：消息失败主 owner、次级 observer 摘要和显式
  `localExecutionId + diagnosticCode + phase` 归并合同；不同阶段隔离
- `[DONE] W4`：`CancellationException` 保持取消语义，服务销毁取消不再进入业务 ERROR 回调
- `[DONE] W5`：`UIHierarchyManager` 单一 applicationContext owner、绑定状态机、精确登记清理
  和失败分类
- `[DONE] W6`：ANR 启动/首帧/用户操作阶段与连续延迟观测；TextSegmenter 预热、队列等待、
  线程和首次真实搜索观测；未改变阈值、调度器或预热时机

当前仍未完成的工作：

- relay 具体连接中止根因仍未知
- Pixel/其他设备上的现场矩阵仍未执行
- 用户可见 UIHierarchy provider 状态、启动性能和 TextSegmenter 时间线仍待设备验收

本地 Debug APK 最终构建与静态产物审计已经完成，以下 W7 证据只覆盖本地实现和静态制品，
不替代 relay、设备或用户验收。

## 2.2 W7：最终本地验证与制品审计证据

本节中的构建宿主观察时间为 `2026-08-13 03:56:14 +08:00`。它是跨越创建日后的事件时钟证据，
不改变本任务以 `2026-08-12` 为相对日期依据的日记归档约束。

### 定向测试矩阵

最终 `:app:testDebugUnitTest` 定向矩阵共 `51` 个测试，失败、错误和跳过均为 `0`：

| 测试类 | 通过数 |
| --- | ---: |
| `AIForegroundServiceCancellationTest` | 2/2 |
| `ApiKeyProviderLogPrivacyTest` | 2/2 |
| `LlmLogPrivacyTest` | 5/5 |
| `LlmTransportDiagnosticsTest` | 4/4 |
| `OpenAIResponsesSubmissionFaultInjectionTest` | 3/3 |
| `UIHierarchyBindingSessionTest` | 10/10 |
| `MessageProcessingDelegateTest` | 3/3 |
| `AnrMonitorObservationTest` | 3/3 |
| `TextSegmenterDiagnosticsTest` | 2/2 |
| `HotStreamFailurePropagationTest` | 10/10 |
| `NativeMarkdownSplitterTest` | 3/3 |
| `KiyoriActivityLifecycleFactsTest` | 4/4 |
| **合计** | **51/51** |

验证覆盖了 Responses 传输阶段和未知提交状态、请求日志隐私、错误 owner 归并、服务取消、
UIHierarchy 绑定状态、ANR/TextSegmenter 观测及生命周期事实。故障注入用例确认 response-header
前失败不会产生第二个 POST。

### 工程门禁与 Debug 构建

已执行并通过：

```text
.\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main
Formal development readiness: PASS

git diff --check
无 whitespace error；仅有既有 CRLF 转换警告

	.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
BUILD SUCCESSFUL
238 actionable tasks；25 executed；213 up-to-date
verifySingleDebugLauncher: passed
verifyDebugPlayerRuntimePackaging: passed
```

最终 Debug APK：

```text
path:
D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk

generated:
2026-08-13 03:56:14 +08:00

size:
472480557 bytes

SHA-256:
D57921CB207114643FBBC6428E8ED3457930514FF264CB571A9A67239DD7192D
```

APK 元数据与静态审计：

- `applicationId=com.kiyori`
- `versionCode=45`，`versionName=0.1.0`
- `minSdk=26`，`targetSdk=34`，`compileSdk=37`
- 唯一 launcher：`com.ai.assistance.operit.ui.main.MainActivity`
- native ABI：仅 `arm64-v8a`
- `.so` 数量 `51`，重复 basename `0`
- Android Debug 签名：V2 `true`、V1 `false`、V3 `false`，单 signer，证书为 Android Debug
- `zipalign -c -P 16 -v 4`：`Verification successful`
- ELF 审计：`51` 个 `.so` 加 `assets/operit_shell_exec` 共 `52` 个 AArch64 ELF；
  `PT_LOAD` 共 `160` 个，最小对齐 `0x4000`，低于 `0x4000` 的 segment 为 `0`

内置 ToolPkg 与敏感制品扫描：

- `assets/packages/openai_web_search.toolpkg`：`9864` bytes
- ToolPkg SHA-256：
  `558382BDDE9688F99395F703D3225C7DAB5452326F85A6660DDB557ACEA3B9FB`
- APK 内 ToolPkg 与生成制品逐字节一致
- `Authorization Bearer`、`sk-* credential shape`、长 API key assignment、private key block、
  cookie credential shape：全部为 `0`

因此 W7 的本地验收状态为 `PASS`。该状态只证明当前 dirty worktree 的本地实现、测试、准备
检查和 Debug 静态制品满足本轮门禁，不证明 relay 已修复、设备行为正确或用户已经验收。

## 3. W0：准备与保护边界

### 修改范围

本阶段默认不修改业务源码，只建立实施前检查结果：

- `git status --short --branch`
- 当前 branch、HEAD、tracking ref
- 相关文件的已暂存、未暂存和未跟踪状态
- `docs/TODO/openai_hosted_web_search/` 当前文档树
- 现有 OpenAI Web Search dirty changes 的精确路径清单

### 保护要求

- 不使用 `reset --hard`、`checkout --`、`clean` 或广泛删除
- 不覆盖用户现有源码和文档改动
- 不把原始日志复制到仓库
- 不把 Key、Authorization、Cookie、完整 URL 查询参数、完整 prompt 或私密路径复制到任何
  测试 fixture、日志、文档或 APK
- 不提交和推送，除非用户在后续当前任务中明确授权

### 完成判据

工作树基线和允许修改清单可以被后续会话复核，且没有私密内容进入工作区文档。

## 4. W1：Responses 传输诊断与未知状态定位

### 4.1 目标

把当前 `AIHttpTrace` 从自由文本阶段日志扩展为受控的传输诊断快照，同时保持现有
`OpenAIResponsesSubmissionUnknownException` 和 at-most-once 行为。

### 4.2 候选文件

```text
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/AIServiceFactory.kt
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIProvider.kt
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIResponsesStreamState.kt
app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/
	OpenAIResponsesHttpFailurePolicyTest.kt
	OpenAIResponsesSubmissionFaultInjectionTest.kt
```

### 4.3 诊断字段

推荐记录：

- `requestTraceId`
- `localExecutionId`
- provider/model 的非秘密标识
- stream、attempt 和执行持久化模式
- endpoint 的稳定 label，不输出完整 query 参数
- request body byte count
- request/response stage
- protocol、TLS version、cipher kind
- connection reuse kind
- response headers 是否开始、是否结束
- HTTP status code（仅在确实到达时）
- response ID/request ID/trace ID 的脱敏值
- 错误分类、底层异常类型和受限消息
- observer count 和最终 terminal outcome

不得记录：

- Authorization、Cookie、Key 的任何前后缀
- 完整 request body
- 完整 prompt、用户消息、工具定义
- 完整自定义 headers
- 可能含 token 的 query string

### 4.4 测试

使用本地 MockWebServer 或等价的受控 HTTP fixture 覆盖：

- 请求在 response headers 前断开
- response headers 到达但 response body 中断
- HTTP 4xx/5xx 已收到状态码
- stream 已收到部分事件后断开
- 用户取消导致的取消异常
- 每一种状态是否映射到正确的 diagnostic code
- response-header 前失败仍只产生一次未知提交状态，不产生第二个 POST

测试必须断言“请求次数”和“持久化终态”，不能只断言异常文本。

### 4.5 现场验证

用户明确授权真实 relay 请求后，按单变量矩阵记录：

1. 同一 Key、模型和 endpoint 的普通 Responses 最小请求
2. 最小 hosted web search
3. 当前 Kiyori 完整请求
4. 只改变一个请求维度的对照
5. 同一配置在 Pixel 与另一 Android 设备的对照

每条只保留状态码、事件阶段、协议、错误码、响应 ID/trace ID 的脱敏形式和计费提示。禁止自动
重复提交；每次现场请求都必须由用户明确确认可能产生费用。

### 4.6 完成判据

- 本地 fixture 能稳定区分 response-header 前失败、headers 后失败和 HTTP 错误
- 失败日志能回答“是否收到响应头”和“是否收到响应 ID”
- 未知提交状态仍不会被重复提交
- relay 具体根因若仍未知，文档明确保留未知项，不得写成已修复

## 5. W2：请求日志与凭据隐私修复

### 5.1 修改范围

```text
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/ApiKeyProvider.kt
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIProvider.kt
app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/AIServiceFactory.kt
```

如仓库已有统一诊断摘要 owner，应复用该 owner；不要为单个 provider 创建第二套脱敏系统。

### 5.2 API Key 合同

- 删除单 Key、多 Key、Key pool 的前缀和后缀输出
- 不输出 `sk-`、末尾四位或任何可关联原始 Key 的片段
- 如必须关联同一配置的轮换事件，使用已有兼容性合同中的不可逆 fingerprint/revision
- fingerprint 不得可逆、不得包含 Key 片段、不得跨不相关配置共享
- 增加测试扫描日志模板和异常文本，防止后续重新引入前后缀

### 5.3 请求正文合同

将当前“完整 JSON 只省略 tools”的行为改为结构化摘要：

- 请求字节数
- 历史消息角色计数
- 用户内容长度统计
- 工具数量和工具名称的受控集合摘要
- model/provider/stream/protocol
- 非可逆请求合同 digest
- 明确的 debug 授权状态

默认不保存 prompt 内容。若未来需要临时 debug 模式，必须有：

- 明确的本地开关和作用域
- 时间或会话生命周期
- 最大字符数
- 用户内容和凭据脱敏
- 退出 debug 后不继续记录旧正文

### 5.4 自动验证

- API Key 日志安全扫描为 0
- request body 日志中不出现测试用户消息、系统提示词、Authorization、Cookie、Key 片段
- 日志摘要保留请求大小和阶段定位能力
- 对 DeepSeek、OpenAI Responses 和其他共享 `OpenAIProvider` 路径做回归

### 5.5 完成判据

普通诊断日志可以安全地用于问题定位，不需要先手工删除私聊、Key 或完整工具合同。

## 6. W3：错误所有权与日志降噪

### 6.1 候选文件

```text
app/src/main/java/com/ai/assistance/operit/util/stream/StreamFailureOwnership.kt
app/src/main/java/com/ai/assistance/operit/api/chat/
app/src/main/java/com/ai/assistance/operit/ui/
```

实际修改前先用 `rg` 画出 `OpenAIResponsesSubmissionUnknownException`、`onFailure`、
`localExecutionId` 和完整 stack 输出的调用链，避免凭标签猜测 owner。

### 6.2 处理合同

- 主消息 owner 是唯一完整 cause chain 记录者
- 次级观察器只能报告 observer name、阶段和已聚合状态
- 同一 `localExecutionId` 的相同 diagnostic code 只输出一次完整堆栈
- 不同异常 cause 或不同阶段仍需保留独立记录
- `turn terminal` 输出唯一诊断码、阶段、chunks、visibleChars 和 observer count
- 异常继续沿主 owner 传播，不能用 `runCatching`、空 catch 或日志过滤掩盖真实失败

### 6.3 自动验证

- 一次 provider failure 在日志 fixture 中只有一个完整 stack owner
- 次级观察器结束但不会触发第二个全局失败
- 主 owner 仍保存原始异常类型和 cause
- 取消与 provider failure 不被混为同一诊断码

## 7. W4：AIForegroundService 生命周期取消

### 修改文件

```text
app/src/main/java/com/ai/assistance/operit/api/chat/AIForegroundService.kt
```

### 实施要求

- `CancellationException` 单独处理并保持取消语义
- 服务销毁导致的正常取消不记录 ERROR
- 非取消 Flow 异常继续记录 ERROR 和完整 stack
- 不改变偏好值、overlay 状态、通知或服务停止策略

### 测试

- service scope 取消时无 ERROR
- Flow 主动抛出非取消异常时保留 ERROR 和 cause
- service 再启动后两个观察器都能重新建立
- 取消不会被误记为 provider failure 或用户操作失败

## 8. W5：UIHierarchyManager 绑定合同

### 修改文件

```text
app/src/main/java/com/ai/assistance/operit/data/repository/UIHierarchyManager.kt
```

### 实施要求

先固定状态模型，再决定实现细节：

```text
NOT_INSTALLED
SERVICE_UNRESOLVED
DISABLED
NOT_EXPORTED_OR_PERMISSION_MISMATCH
BIND_RETURNED_FALSE
WAITING_FOR_CONNECTION
CONNECTED
TIMEOUT
CONNECTION_FAILED
UNBOUND
```

要求：

- `bindService()` 返回 false 时不能打印成功完成
- 绑定动作只拥有一个明确的 Context owner
- 解绑只针对实际成功绑定的 Context
- timeout、返回 false、SecurityException、连接回调失败分开记录
- 记录 ServiceInfo 的非秘密属性和当前前后台状态
- `isProviderAppInstalled()` 的返回值、日志文本和 `_isBound` 语义一致
- 不把“未安装”“已绑定”“绑定失败”合并到同一条提示
- 不在 UI 层添加自动切换 provider 或无限尝试逻辑

### 测试

- provider 未安装
- service 无法解析
- service disabled
- bind false
- SecurityException
- connection callback false
- timeout
- 成功绑定后只释放一次
- 绑定失败后的重复调用不会误报成功

真实 OEM provider 行为仍需在设备上单独验收。

## 9. W6：启动性能观测和决策

### 9.1 ANR 监控

先增加诊断字段，不立即改变阈值：

- startup phase / user operation phase
- consecutive delayed samples
- total warning count
- max block duration
- process lifecycle state
- 是否在首帧前后

`reportSlowResponse()` 的全仓生产和测试引用审计结果为 0 个有效调用方。该接口及其独立统计
路径已删除，所有延迟样本统一由 watchdog 采样路径记录，避免保留两个互不一致的警告统计来源。

### 9.2 TextSegmenter

增加冷启动、热启动和首次真实查询的时间字段：

- initialize 调用次数
- dictionary load duration
- dispatcher/thread
- queue delay
- first search duration
- prewarm completed before/after first search

根据至少多次冷启动样本决定：

- 保持首帧后异步预热并降低与其他大任务的并发竞争
- 或把词典加载迁移到首次真实 memory search 前的明确阶段

选择必须以用户可见延迟、启动警告次数和总 CPU/IO 代价为依据，不以单条 4715ms 日志直接改动
初始化策略。

### 9.3 完成判据

- 能区分一次抖动与连续主线程饥饿
- 能判断 TextSegmenter 是独立慢任务还是启动资源竞争参与者
- 没有把异步耗时误报为首屏阻塞

## 10. W7：本地验证矩阵

按改动风险执行，不机械扩大到无关专项：

1. 受影响 Kotlin 单元测试
2. 网络失败 fixture 和日志脱敏测试
3. AIForegroundService 生命周期测试
4. UIHierarchyManager 绑定状态测试
5. `git diff --check`
6. 相关 Markdown 链接和敏感内容扫描
7. `python -B ci/script/check_formal_readiness.py --repository . --require-main`
8. 需要时执行相关架构门禁和 AndroidTest 编译
9. 串行执行：

   ```text
   .\gradlew.bat :app:assembleDebug --no-daemon --console=plain
   ```

10. 核验 Debug APK 路径、时间、大小、SHA-256、application ID、唯一 launcher、签名和 16 KB
    对齐；构建证据与设备验收分开报告。

如果本轮只改文档，仍需按项目 `AGENTS.md` 的收尾规则执行 Debug APK 构建；当前本轮写入的是
分析文档，因此后续本轮仍应完成该门禁。

## 11. W8：relay 与设备验收

只有用户明确授权可能产生费用的真实请求和设备操作后执行。

### 11.1 relay 验收记录

每次记录：

- 绝对时间和时区
- 设备型号、Android/OEM 版本
- endpoint label、model label、认证 scheme kind
- API key revision/fingerprint，不记录 Key
- request body bytes 和 diagnostic code
- protocol/TLS kind
- response header/body/stream 阶段
- HTTP status、response ID/trace ID 的脱敏值
- usage、web search call count、evidence mode
- 是否产生费用的用户确认

禁止记录完整 request、完整 response、完整 headers、Key 或用户原始内容。

### 11.2 最小矩阵

按单变量原则执行：

1. 普通 Responses
2. OpenAI 官方最小 hosted web search
3. Kiyori 当前完整请求
4. 只改变一个字段的请求
5. 同一配置新建 HTTP client 的诊断对照
6. 同一配置另一 Android 设备对照

每个请求只执行一次。发生未知提交状态时停止当前 case，不进行第二次相同 POST。

### 11.3 设备功能验收

- ToolPkg 安装、启停、版本和插件分类
- 环境变量输入遮蔽、保存、清除和 host-only 隔离
- compatibility probe 的付费确认和失败状态
- 第三方主模型调用 `openai_web_search:search`
- citation/source evidence card
- cancellation
- UIHierarchyManager provider 状态
- 启动主线程警告和 TextSegmenter 时间线

## 12. 风险、恢复与停止条件

### 可恢复范围

- 文档和测试 fixture 可通过 Git 差异审阅恢复
- 诊断字段增加不改变请求语义
- 日志脱敏修改可通过本地日志安全扫描验证
- 未知提交状态继续保存在本地执行持久化中

### 必须停止并报告的情况

- 需要改变 at-most-once 合同
- 需要新增 endpoint、Key 或账户权限
- 需要向外部 relay 上传完整 prompt、headers 或源代码
- 需要安装 APK、ADB、模拟器或真机操作但用户未明确授权
- 发现当前用户 dirty changes 与目标文件冲突且无法安全隔离
- 本地测试无法证明“没有产生第二个 POST”

### 完成状态定义

- `LOCAL_ANALYSIS_COMPLETE`：本报告和计划已写入，未修改业务源码
- `LOCAL_IMPLEMENTATION_COMPLETE`：W1–W6 代码、测试和本地门禁通过
- `REMOTE_DEVICE_PENDING`：relay/设备现场未完成
- `COMPLETE`：本地实现、必要现场矩阵和用户验收全部完成

本计划当前状态为：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_PENDING
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
```

本地实现完成不等于 relay 根因已修复，也不等于设备或用户验收完成。下一步入口是最终本地
门禁与 APK 审计；现场矩阵必须在用户明确授权可能产生费用的真实请求和设备操作后执行。
