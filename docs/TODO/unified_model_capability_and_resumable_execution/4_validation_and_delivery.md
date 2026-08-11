# 4. 验证与交付

## 自动验证

### 数据库

- 20 -> 21 migration
- 外键删除
- event unique key
- cursor monotonicity
- tool call unique key
- active execution query

### Responses 状态机

- sequence 1 created -> in_progress -> completed
- created -> disconnected -> resumed -> completed
- duplicate sequence
- sequence gap
- EOF without terminal
- failed/incomplete/cancelled
- user cancel
- submission unknown
- 配置刷新期间的活跃 service lease
- 非用户和未知取消的可见错误终态

### 工具

- function call 重复事件
- 重连后不重复执行
- 参数哈希冲突
- 工具完成后进程重建
- 多 hop 的 call ID 与 output 顺序

### 请求

- 五档 reasoning
- Pro
- Fast
- reasoning context
- Background
- Prompt Cache
- Tool Search
- strict schema

## 故障注入

使用本地测试服务器或可注入 transport 模拟：

- response.created 前断线
- response.created 后首 token 前断线
- reasoning summary 中断
- output text 中断
- function arguments 中断
- function call 完成后断线
- response.completed 前 EOF
- App execution owner 重建
- Provider 在 UI 订阅前失败，SharedStream 向晚到收集器重放同一失败类型和消息
- `response.created` 前 HTTP 502 持久化 `SUBMISSION_UNKNOWN`，精确断言只 POST 一次
- 下游收集器收到上述失败，SharedStream owner 的 `CoroutineExceptionHandler` 不收到异常，
  不生成 `APP_FATAL`
- 消息主收集 Job 把原异常交给主发送 owner 后正常完成；同时订阅的次级观察器也正常结束，
  两者都不触发 `CoroutineExceptionHandler`
- 主收集器和次级观察器被取消时保持 `CancellationException`，不报告普通失败
- 全量配置刷新只退休缓存实例；活跃 lease 归还前不取消或释放 Provider，新请求不取得
  retired 实例
- 用户停止与破坏性历史修改按当前 chat/turn ID 静默进入 `Idle`；配置、生命周期和未知取消
  进入可见 `Error`，旧回合取消标记不污染下一轮

不调用真实付费模型 API。

## 本地门禁

1. 定向 JVM/Android 测试
2. Kotlin 编译
3. 项目 `.venv` formal readiness
4. `git diff --check`
5. `./gradlew :app:assembleDebug --no-daemon --console=plain`
6. Debug APK 路径、包名、版本、签名和 16 KB 对齐核验
7. `ci/script/check_message_processing_dex.py` 审计最终 APK 的发送 continuation

## 当前证据

- `ManagedServiceLeaseStateTest`、`AssistantTurnCancellationTest`、
  `MessageProcessingDelegateTest`、`AssistantResponseCompletionPolicyTest`、
  `ModelRequestCompilerTest`、`OpenAIResponsesHttpFailurePolicyTest`、
  `OpenAIResponsesSubmissionFaultInjectionTest`、`OpenAIResponsesTerminalSnapshotTest`、
  `HotStreamFailurePropagationTest` 与 `ChatMarkupRegexTest`：`63/63` 通过，
  失败、错误和跳过均为 `0`
- 生产链 502 回归从公开 `OpenAIProvider.sendMessage` 流进入可恢复 Responses 协调器，
  loopback HTTP 服务返回首个 `response.created` 前的 502；精确断言请求序列只有一个
  `POST /v1/responses`，生产 repository 适配器只创建一次 `SUBMITTING` execution 并写入一次
  `SUBMISSION_UNKNOWN / HTTP_502_SUBMISSION_UNKNOWN`，没有 append event、begin resume 或
  第二个网络请求，收集者得到原 `OpenAIResponsesSubmissionUnknownException`
- SharedStream 回归测试验证晚到收集器得到相同异常类型和消息；消息所有权测试验证主收集
  Job 把原始类型和消息交给主任务后正常完成，同时次级观察器也正常完成，owner scope 的
  `CoroutineExceptionHandler` 保持未触发。主收集器和次级观察器的取消测试继续保留
  `CancellationException`
- 完整 `:app:testDebugUnitTest`：`175` 个 suite、`1033/1033` 通过，失败、错误和跳过均为 `0`
- `:app:compileDebugAndroidTestKotlin`：通过，`146` 个任务中 `2` 个执行、
  `144` 个为最新状态
- migration、DAO、stream state、Tool Search 和 provider tool identity 的 AndroidTest：
  已编译；新增原始 output 位置、done-only 参数快照、当前回合 call ID 去重和历史一一对应
  测试，未在设备上运行
- 项目 `.venv` formal readiness：PASS
- 最终 `git diff --check`：通过，仅有工作树既有 CRLF 转换提示
- 代码构建 `:app:assembleDebug --no-daemon --console=plain`：
  确认没有其他 Gradle 进程后串行执行，`BUILD SUCCESSFUL in 32s`，`238` 个任务中
  `25` 个执行、`213` 个为最新状态
- `verifySingleDebugLauncher`：唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`
- `verifyDebugPlayerRuntimePackaging`：通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`485889789` bytes，
  SHA-256 `E43FB70208FD9F59B87D009D537C46A6524437A3480834A9B5508ED98BFDBE3F`
- APK 元数据：`com.kiyori`、`0.1.0 (45)`、min/target/compile SDK `26/34/37`、唯一
  `arm64-v8a`
- APK 内容：`51` 个 native 库且 basename 无重复，包含
  `lib/arm64-v8a/liboperit_ripgrep.so` 与 `assets/operit_shell_exec`，不包含
  `libsudo.so`
- Android Debug V2 单 signer 签名通过；`zipalign -c -P 16 -v 4` 为
  `Verification successful`

### Native ART DEX 兼容性核验

用户现场的 vivo PD2507 / Android 16 tombstone 为：

```text
signal 6 (SIGABRT)
Abort message: Unexpected instruction: unused-e6
Java frame: MessageProcessingDelegate$sendUserMessage$sendJob$1.invokeSuspend
```

旧 APK 中，整轮发送被编译进单一 `executeSendUserMessageTurn` continuation，约为
`971 registers / 584520` 字节级方法体。当前 APK 中已核验：

- `MessageProcessingDelegate$sendUserMessage$sendJob$1.invokeSuspend`：`35 registers`
- `MessageProcessingDelegate$executeSendUserMessageTurn$1.invokeSuspend`：`33 registers`
- `executeSendUserMessageTurn` 方法体：`20755` 字节级方法体
- `MessageProcessingDelegate$collectAssistantResponseStream$2.invokeSuspend`：
  `30 registers`，约 `1807` 行反汇编输出
- `prepareSendUserMessageTurn`、`prepareAssistantRequest`、`submitAssistantResponse`、
  `startAndAwaitAssistantResponseCollection` 与 `completeAssistantResponse` 均为独立挂起阶段

这组证据只证明 APK 的 DEX 状态机已从单一巨大 continuation 拆开，不能替代 vivo 设备安装
后的真实发送复测；设备状态继续为 `verification_pending`。

## 独立验收

- 真实 OpenAI endpoint smoke test 需要用户单独授权和费用
- Android 网络切换、退后台和进程重建需要目标设备验收
- 本地构建不能代替真实 endpoint 或设备结果
- 当前交付状态为 `verification_pending`
