# 日志驱动的浏览器、播放器与代理链路修复

> 状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK AUDIT COMPLETE / TARGET DEVICE VERIFICATION PENDING`
>
> 本阶段基于 2026-08-27 的 vivo Android 16 真机浏览器诊断、播放器诊断和主进程代理日志，
> 修复 Browser 媒体候选经主进程 `PlayerSession` 交给独立 `:player` 进程后重复启动 Mihomo、
> 本地媒体桥丢失上游错误证据，以及三个诊断面无法对齐同一次请求的问题。Kiyori 尚未公开发行；
> 本轮是现有代理桥方案的根因修订，不保留错误的跨进程并行代理 owner。

## 1. 目标、范围与非目标

### 目标

1. `KiyoriNetworkProxyManager`、`KiyoriMihomoRuntime` 和 `PlayerMediaStreamBridge` 的代理请求端
   只在主进程工作；`:player` 继续只持有唯一 mpv core、Surface、媒体描述符和 native 诊断。
2. 每个网络媒体请求在主进程固定一次 `PLAYER` 路由。需要应用代理的 HTTP/HTTPS 媒体由主进程
   bridge 使用该固定路由请求上游；`:player` 只读取 capability URL，不读取代理配置、不启动 core。
3. bridge 对 mpv 返回合法 HTTP 响应，并把上游 HTTP 状态、连接阶段和有界错误类型投影到现有
   `PlayerDebugLogBuffer`。`503`、连接失败和 bridge 生命周期错误不能再全部折叠为
   `loading failed`。
4. Browser 诊断把媒体候选、播放器交接和当时主进程代理 runtime 快照关联到同一个
   session/document/candidate；网页 console 错误继续标记为第三方页面证据，不冒充 Kiyori 故障。
5. 以定向 JVM/合同测试、完整相关回归、正式开发门禁、Debug APK 与真机复测矩阵证明修复。

### 非目标

- 不更换 Android System WebView provider，不创建第二 Browser Runtime、第二播放器或第二代理核心。
- 不启用 Android VPN/TUN，不修改系统代理，不切换节点，不增加自动重试、直连或任何其它回退路径。
- 不把上游节点真实 `503` 伪装成可由客户端消除的问题，也不把第三方页面的 JavaScript console
  错误归因于 Kiyori。
- 不改变媒体候选排名、页面媒体播放状态、Surface lease、mpv native closure、解码器、Anime4K、
  下载器或应用代理规则语义。
- 不记录 Cookie、Authorization、请求头值、查询参数值、节点地址、代理地址、IP/DNS、私有路径或
  capability token。

## 2. 现场时间线与证据等级

| 本地时间 | 已验证事件 | 结论 |
| --- | --- | --- |
| `15:38:36.904` | 主进程开始代理协调 | 配置要求应用代理，系统 VPN 未启用。 |
| `15:38:39.046` | 主进程 Mihomo `generation=1`、`mixedPort=40877` 健康 | 主进程 listener 与 Controller 正常，不是 core 启动失败。 |
| `15:38:39.078-39.452` | Browser session/provider/能力快照完成并开始 `go.itab.link` 导航 | WebView provider 为设备 `com.google.android.webview 150.0.7871.181`。 |
| `15:38:39.811-39.978` | 页面自身脚本产生两条 console error | 这是第三方页面错误；没有主文档错误、SSL 错误或 renderer 退出证据。 |
| `15:38:39.842` | 同一 document 发现 `v-5.mp4` 候选 | candidate 为可执行 `VIDEO`，发现链路有效。 |
| `15:38:40.634-43.247` | 主进程 Mihomo 对 analytics 和目标媒体域名的上游连接返回 `503` | 目标节点/上游链路在现场不可用；WebView 仍可提交/完成主文档，不等于媒体请求成功。 |
| `15:38:41.730-41.753` | `PlayerSession` 打开请求，Browser 记录 fullscreen handoff | Browser -> PlayerSession 交接完成，页面在 `15:38:43.105` 仍正常结束导航。 |
| `15:38:45.461-45.478` | `:player` 初始化时出现新的 Mihomo `generation=1`、`mixedPort=42071` | 进程内单例被复制，`:player` 启动了第二个 Mihomo；端口差异不是同一 runtime 漂移。 |
| `15:38:45.561` | `:player` 把媒体改写为本地 bridge，mpv route 为 `PROXY_BRIDGE` | 当前 bridge 和其代理 client 归属于错误的 `:player` 进程。 |
| `15:38:46.850-46.854` | mpv 先见 `HTTP response: End of file`，再报 `loading failed` 和本地 bridge URL 无法打开 | mpv 已到达 bridge；本次不是 IPv4 listener 未启动。bridge 的异常路径未返回合法错误响应，丢失上游原因。 |

### 已证实根因

1. `KiyoriApplication.onCreate()` 在每个应用进程创建 `KiyoriNetworkProxyManager`，而
   `PlayerMediaResolver` 和 `MpvPlayerEngine` 又在 `:player` 直接取该 Manager。Kotlin/JVM singleton
   仅在进程内唯一，因此主进程和 `:player` 分别拥有 Manager、Runtime、generation 与端口。
2. HTTP/HTTPS 的 `PlayerMediaResolver.resolve()` 位于 `PlayerRuntimeService.load()` 内，导致
   route 解析和 bridge 只能发生在 `:player`。这与“应用级唯一代理 owner”及文档声称的“bridge
   不创建第二 Mihomo”冲突。
3. `PlayerMediaStreamBridge.handle()` 捕获异常后只写 Android log 并关闭 socket，未向 mpv 写入
   HTTP 错误响应，也未把失败类型发送到主进程诊断。mpv 因而只能观察 EOF，最终可见错误退化为
   通用 `loading failed`。

### 已排除项

- bridge 显式绑定 IPv4 `127.0.0.1`，且 mpv 已对该 URL 发起 HTTP 请求；没有证据支持再次修改
  地址族或 listener bind。
- Android 网络快照显示 Wi-Fi active/validated、无 captive portal、无 background restriction；没有
  证据支持启用 VPN、绑定 Network 或修改 Private DNS。
- Browser 日志没有 `MAIN_DOCUMENT_ERROR`、`SSL_ERROR` 或 `RENDERER_GONE`；页面 console error
  不能证明 Kiyori WebView 崩溃。
- 目标节点返回的 `503 Service Unavailable` 是外部事实；本轮只能准确保留并呈现，不能在客户端
  伪造节点可达性。

### 仍需设备验证的未知项

- 当前选中节点恢复可用后，同一媒体是否完整支持 Range、seek、完整缓存与 HLS 子资源重写。
- 主进程 bridge 跨 Binder 启动、Surface 转挂、播放器关闭和 runtime death 时的真实时序。
- WebView console 的具体第三方脚本文本仍只存在于 Browser console owner；结构化诊断继续不复制正文。

## 3. 目标所有权与调用链

### 3.1 唯一主进程网络 owner

```text
Browser media candidate
  -> main-process PlayerSession
  -> PlayerMediaTransportResolver.resolveNetworkTransport(request)
       -> content/file: keep the original URI for :player local descriptor/path ownership
       -> HTTP(S) DIRECT: original URL
       -> HTTP(S) PROXY: main-process PlayerMediaStreamBridge
            -> one frozen PLAYER route snapshot
            -> existing main-process Mihomo endpoint
  -> Binder PlayerRuntimeLoadRequest(resolvedTarget, original diagnostic identity)
  -> :player PlayerRuntimeService
  -> one MpvPlayerEngine.loadfile(resolvedTarget)
```

- `PlayerSession` 持有当前请求对应的唯一网络 transport resolver。新请求先关闭上一 resolver；播放器
  close、runtime death、绑定失败和自然终止均关闭它。
- HTTP/HTTPS 代理媒体的 bridge listener 位于主进程，但仍只监听 IPv4 loopback 并使用随机
  capability path。`:player` 与主进程同 UID，mpv 可读取该 listener；LAN 无法连接。
- route 在 bridge 创建时通过同一次 Manager 解析冻结，后续 Range/HLS 子资源请求继续使用同一
  selector，不在一个媒体请求内跨 generation 或端口。
- `:player` 不再观察 Mihomo runtime，也不再设置 mpv `http-proxy`。代理媒体已由主进程 bridge
  执行；DIRECT 媒体由 mpv 原样请求。两条路径由主进程的明确 route 决策决定，不存在隐式替代。

### 3.2 IPC 合同

`PlayerRuntimeLoadRequest` 增加明确的传输语义，而不是让 `:player` 从 URL 形状猜测业务配置：

- `uri`：主进程已经解析完成、可直接由 mpv 消费的 target。
- `originalUri` 或等价的脱敏身份字段：仅用于诊断/缓存网络身份，不参与第二次路由。
- `transport`：`DIRECT / MAIN_PROCESS_PROXY_BRIDGE / LOCAL_DESCRIPTOR` 等封闭枚举或稳定 ID。
- `headers`：保留现有不可变原始请求头 snapshot；bridge 消费上游字段，mpv 的 bridge 请求只保留
  它需要的 Range/条件字段，避免把 Cookie 等值再次发给 loopback。

Parcelable 初始化必须拒绝空 target、未知 transport 和非法 header。AIDL 仍传一个不可变 request，
不新增第二服务或共享可变数据库。

### 3.3 错误与诊断所有权

- bridge 为每个请求分配主请求序列，在开始、上游响应、完成和失败时写入现有
  `PlayerDebugLogBuffer`。日志只含 request 短 ID、传输类型、HTTP 状态、Range 是否存在、异常类和
  有界阶段，不含 URL/query/header/token/endpoint 值。
- 上游返回非成功 HTTP 时，bridge 原样向 mpv 转发状态与有限响应头，不读取或复制错误正文。
- 在写出响应头之前发生 IOException 时，bridge 写入确定的 `502 Bad Gateway`；本地协议/资源错误
  继续使用 `400/404/405`。在响应体中途失败时记录 `UPSTREAM_BODY_FAILED`，连接关闭使 mpv 保留
  已收到的 HTTP 状态和断流证据。
- `MpvPlayerEngine` 保留“第一条具体网络错误优先于通用 `loading failed`”的现有合同，但移除
  `:player` 内的代理 route 设置和 runtime 观察。主进程 bridge 的具体错误通过共享缓冲直接可见。
- Browser `PLAYER_HANDOFF` 记录 candidate 短 ID、presentation、主进程 runtime generation、route
  与 transport；不得记录 mixed/controller 端口。第三方 `CONSOLE_ERROR` 保持独立 WEBVIEW 类别。

## 4. 实施阶段与影响文件

### M0 方案与基线

- [DONE] 读取三份真机日志、当前 `main@d10c451f`、项目规则、formal readiness 与现有阶段 13/16。
- [DONE] 区分已证实根因、外部 503、已排除项和待设备验证项。
- [DONE] 在本文件、总 index 和 `docs/TODO/README.md` 登记方案；代码写入前保持工作树仅有文档。

### M1 主进程媒体解析与生命周期

- [DONE] 修改 `PlayerSession.kt`：主进程创建/替换/关闭 resolver，发送解析 target 与 transport。
- [DONE] 修改 `PlayerMediaResolver.kt`：保留 content/file 行为，把 HTTP(S) route/bridge 归属明确为主进程；
  bridge 接受冻结 selector 与诊断 sink。
- [DONE] 修改 `PlayerRuntimeModels.kt`、`PlayerRuntimeConnection.kt`：收紧 IPC request 合同和诊断字段。
- [DONE] 回收点覆盖新 request、发送失败、close、runtime disconnect/death、队列切换和 session 终态。

### M2 清理 `:player` 并行代理 owner

- [DONE] 修改 `PlayerRuntimeService.kt`：删除 HTTP(S) route 解析、bridge owner、Mihomo runtime collector 和
  动态代理刷新；仅解析已传入的 content/file target 或直接加载 resolved target。
- [DONE] 修改 `MpvPlayerEngine.kt`：删除 `KiyoriNetworkProxyManager` 依赖、`http-proxy` 写入和 runtime
  generation 状态；保留 `transport` 诊断、TLS、header plan、cache 和 mpv 具体错误优先级。
- [DONE] 修改 `KiyoriApplication.kt`：只有主进程初始化 `KiyoriNetworkProxyManager`；`:player`、`:ffmpeg`、
  `:crash` 和 `:repair` 不得创建 proxy Manager/Runtime 或执行 stale-runtime cleanup。
- [DONE] 增加生产合同检查，禁止 `core/player/runtime/` 再引用 `KiyoriNetworkProxyManager` 或
  `KiyoriMihomoRuntime`。

### M3 bridge HTTP 语义与可观察错误

- [DONE] 为 request-line/header 上限、GET/HEAD、Range/If-Range、HLS URI、HTTP 状态、响应长度与连接关闭
  建立纯测试able contract。
- [DONE] 异常发生在响应头前时发送合法 `502`，同时记录 `UPSTREAM_CONNECT_FAILED` 或
  `UPSTREAM_RESPONSE_FAILED`；响应体中途失败记录独立阶段。
- [DONE] 非 2xx 状态原样转发，不自动重试、不换节点、不直连、不读取未知错误正文。
- [DONE] bridge 关闭与新请求替换保持幂等，并等待/取消自身 executor，不影响唯一 mpv runtime。

### M4 Browser 与 Player 关联诊断

- [DONE] 扩展 Browser handoff 记录：candidate ID、当前 document token、presentation、request ID 与
  主进程 transport owner；主进程 `PlayerSession` 随后在同一 request ID 下记录 route generation 与
  transport，字段通过 `BrowserDiagnosticModels` 脱敏白名单。
- [DONE] Player 报告增加 main-process bridge transport，并保留按 request ID 对齐的 bridge phase/HTTP
  status 诊断日志；架构文档明确 owner process，不输出 capability URL 或端口。
- [DONE] 代理日志继续由主进程唯一 `KiyoriNetworkProxyLogStore` 持有；用户导出的主进程日志将覆盖 bridge
  请求错误，不再要求从 `:player` 导出第二份代理日志。

### M5 验证、产物与交付

- [DONE] 定向 JVM：bridge HTTP/Range/HLS/502/关闭，resolver transport，IPC 校验，PlayerSession 生命周期，
  Browser handoff 脱敏关联，网络单 owner 合同。
- [DONE] 相关回归：Player policy/runtime event、Browser diagnostic/media candidate、network proxy policy/
  sanitizer/runtime process policy，以及 `ci.test.test_application_network_proxy_contract` 和
  `ci.test.test_player_assets`。
- [DONE] 执行 `check_formal_readiness.py --repository . --require-main`、architecture boundaries、
  Markdown links、`git diff --check`；风险评估后运行完整 `:app:testDebugUnitTest`。
- [DONE] 串行执行 `./gradlew :app:assembleDebug --no-daemon --console=plain`，核验 APK identity、V2 signer、
  16 KB ZIP/native 对齐、唯一 launcher、player runtime packaging 和 SHA-256。
- [DONE] 精确审计允许清单、敏感内容、构建产物、子模块和远端竞争后提交推送唯一 `main`，核对 local、
  tracking 和 `ls-remote`。

## 5. 风险、失败模式与控制

| 风险/失败模式 | 控制与完成信号 |
| --- | --- |
| bridge 随主进程而非 `:player` 存活，播放器进程重启后请求错连旧 bridge | `PlayerSession` 为当前 request 持有 network resolver；runtime restart 会重新建立同 request 的明确 transport，不复用已关闭 bridge，也不创建第二路由 owner。 |
| resolver 在 Binder load 发送后过早关闭 | 生命周期绑定到 PlayerSession request，而不是 command 完成；只有替换、关闭或终态释放。 |
| queue 切换泄露旧 bridge/executor | 新 request 先关闭旧 resolver，再创建新 transport；测试 listener 拒绝旧 capability URL。 |
| bridge 把错误正文或 header 值写入诊断 | 只保留阶段、状态、计数和异常类；复用现有 Player/Browser 脱敏测试反向扫描。 |
| HLS 子资源使用新路由或丢失请求身份 | 所有 rewrite resource 绑定同一 bridge、同一 selector 和同一 request headers snapshot。 |
| `:player` 仍因 Application 初始化而创建 Manager | `KiyoriApplication` 主进程门禁加静态/单元合同；播放器 runtime 源码零 Manager/Runtime 引用。 |
| 上游 503 被误报为本地 bug | proxy log、bridge log 和 mpv log 均保留 `HTTP 503` 层级；Browser console 保持独立类别。 |
| 为了提高成功率引入隐式替代路径 | 明确禁止自动重试、换节点、直连、VPN/TUN、第二 core；测试只接受唯一 route 结果。 |

## 6. 回滚点与完成定义

代码写入前回滚点为干净 `main@d10c451f346eac30e63b24ee3f507897c1de1aa4`。实现按里程碑保持可审查；
若 M1/M2 合同无法同时成立，不提交只移动 bridge 的半成品。不得通过恢复 `:player` 内 Manager、忽略
异常或添加直连分支来绕过失败。

本地完成必须同时满足：

1. `:player` 源码和 Application 启动路径不能创建 `KiyoriNetworkProxyManager`/
   `KiyoriMihomoRuntime`，主进程是唯一 Mihomo owner。
2. HTTP(S) PROXY 请求的 bridge 位于主进程，DIRECT 请求保持原 URL，content/file 保持现有描述符/
   路径合同；一个请求只解析一次 route。
3. 上游 `503`、连接失败、bridge 协议错误和 mpv native 错误在诊断中层级清楚，且敏感数据零泄露。
4. 定向与相关回归、正式门禁、Debug APK 构建和产物审计全部通过。
5. 提交后 `HEAD == origin/main == refs/heads/main`，工作树干净。

以下必须继续标记 `verification_pending`：目标设备使用可用/不可用节点分别复测 HTTPS MP4 与 HLS，
验证 Range/seek/full-cache、Browser 页面无刷新、floating/fullscreen 转挂、播放器关闭、`:player` 崩溃
重启，以及三份导出日志能够按同一个 request 对齐。自动检查和 Debug APK 不能代替这些现场证据。

## 7. 本地实现与自动化证据

- Kotlin 编译：`./gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`，`BUILD SUCCESSFUL`。
- 定向 JVM：`PlayerMediaProxyBridgePolicyTest`、`PlayerRuntimeProtocolPolicyTest`，`BUILD SUCCESSFUL`。
- 完整 JVM：`:app:testDebugUnitTest`，`BUILD SUCCESSFUL`，无失败/错误/跳过。
- Python 合同：`ci.test.test_application_network_proxy_contract` 与 `ci.test.test_player_assets`，`15/15 OK`。
- 架构合同：`ci.test.test_architecture_boundaries`，`109/109 OK`；真实 gate `PASS (phase=m03)`。
- 正式准备：`check_formal_readiness.py --require-main`，`PASS`。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`503669293` bytes，SHA-256
  `6FCFF11D46CBD0F189EB7B7C2236A14A55007EA496266CF02AD41C42B5E15F2A`；Gradle 已通过
  `verifySingleDebugLauncher`、`verifyDebugPlayerRuntimePackaging`。

APK 签名、16 KB ZIP/native 对齐、arm64 native closure、真实可用节点的 MP4/HLS Range/seek、
浏览器页面无刷新、floating/fullscreen 转挂、播放器进程重启以及三份现场日志的跨进程对齐，
仍需目标设备和真实网络现场证据，状态保持 `verification_pending`。
