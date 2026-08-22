# 全脚本宿主代理与 Clash 订阅运行时

## 1. 状态与任务契约

- 当前状态：`M0-M5 IMPLEMENTED / M6 IN PROGRESS / DEVICE VERIFICATION PENDING`
- 基线：`main@e407c42f3621f98a3eea5c9c7bf3176dc56521fd`
- 目标：为“扩展 -> 脚本”中的全部传统 `JsEngine` JavaScript 包提供一个由宿主控制的
  网络路由层。用户可以设置全局模式，也可以让任意已导入脚本继承、直连、使用外部 HTTP
  代理，或使用 Kiyori 内嵌的 Clash 订阅运行时。
- 非目标：不改变 Browser userscript、Browser Runtime、ToolPkg 子包、MCP、AI Provider、
  Terminal、播放器、浏览器下载器或应用其他网络请求；不申请 Android VPN/TUN 权限；不把
  代理地址或订阅内容作为脚本参数；不安装设备、不做 Release 或部署。
- 发布假设：这是对现有脚本设置的能力增量，不替换脚本包 ID、市场协议或公开工具参数。
- 授权：允许读取官方 mihomo 源码和发布物、修改仓库、构建 Debug APK，最终提交并推送
  `origin/main`；不授权安装、ADB、模拟器、Release 或部署。

完成必须同时满足：设计、实现和用户文档一致；四个标准脚本网络入口遵守同一可信脚本身份和
路由快照；敏感配置只存在于 Android Keystore 加密的 no-backup 文件；内嵌核心没有 LAN、TUN
或公开控制面；专项测试、正式开发门禁、Debug APK 与 ELF/签名/对齐审计通过；提交推送后
local、tracking 与远端 `refs/heads/main` 一致。真机节点连通、系统 Clash 共存和视觉交互仍需
单独标记 `verification_pending`。

## 2. 已验证的实施结果

### 2.1 脚本域与身份

1. `JsToolManager.buildRuntimeParams()` 为非 ToolPkg 的传统脚本注入宿主域标记；
   `JsEngine.ExecutionSession` 保存宿主确定的 package name 和 eligibility，二者不从脚本参数读取。
2. 标准 `toolCall()` 使用携带 `root.__operitCurrentCallId` 的 execution bridge。宿主只在 call ID
   仍对应活动 session 且 session 属于传统脚本时注入内部 package 参数。
3. `JsNativeInterfaceDelegates` 无条件删除脚本传入的同名内部参数，再合入宿主可信值；旧低层
   `NativeInterface.callTool*()` 继续兼容，但没有可信 session 时永远得不到代理身份。
4. ToolPkg runtime 不获得传统脚本域标记；设置页也从逐脚本列表排除 ToolPkg 子包。4 项仓库
   静态契约测试锁定身份桥、四入口接线和非脚本网络 owner 不读取代理 store/factory。

### 2.2 全部标准网络入口

| 脚本 API | 宿主工具 | 原传输 | 实施后传输 |
| --- | --- | --- | --- |
| `Tools.Net.httpGet/httpPost/http`、`OkHttp3.js`、直接 `toolCall` | `http_request` | OkHttp | 同一 OkHttp 请求语义，宿主注入路由 |
| `Tools.Net.uploadFile`、直接 `toolCall` | `multipart_request` | OkHttp | 同一 OkHttp 上传语义，宿主注入路由 |
| `Tools.Net.visit`、直接 `toolCall` | `visit_web` | 主进程 WebView | 传统脚本统一改为 OkHttp + Jsoup 文档提取；普通 AI 调用保持 WebView |
| `Tools.Files.download`、直接 `toolCall` | `download_file` | `HttpURLConnection` 多段下载 | 传统脚本改为同一路由层的 OkHttp 原子文件下载 |

QuickJS 没有独立 `fetch`、`XMLHttpRequest`、WebSocket 或原生 Socket。以上四项是传统脚本当前
可直接使用的完整宿主网络表面。`browser_*` 调用操纵独立 Browser Runtime，不纳入代理；脚本
通过 shell、MCP 或 ToolPkg 间接触发的网络也由对应运行域负责，不冒充为脚本 HTTP 请求。

### 2.3 `visit_web` 的隔离结论

AndroidX WebKit `ProxyController` 的覆盖单位不是单个 WebView。主进程同时持有 Browser Runtime、
登录页和其他 WebView，临时切换会让并发页面获得错误路由。因此传统脚本 `visit_web` 固定使用
专用 HTTP 文档提取：跟随 HTTP 重定向、应用调用方 headers/User-Agent、解析最终 URL、标题、
metadata、正文、链接与图片链接，并沿用 visit key 和大正文落盘合同。它不执行页面 JavaScript，
不显示 CAPTCHA 悬浮窗，也不创建 Browser session。普通 AI 对 `visit_web` 的调用仍走现有
WebView，二者由可信脚本身份区分。

## 3. 产品入口与界面

唯一入口保持在：

`AI 对话左抽屉 -> 扩展 -> 脚本 -> 顶栏设置`

设置图标不再直接打开环境变量抽屉，而是打开紧凑的“脚本设置”选择层：

| 条目 | 图标 | 行为 |
| --- | --- | --- |
| 环境变量 | `Key` | 打开现有 `PackageEnvironmentVariablesSheet`，存储和作用域不变 |
| 网络代理 | `NetworkProxy` | 打开全屏“脚本网络代理”设置面 |

`PackageManagerScreen` 继续持有选择层、环境变量 Sheet 和网络设置面的唯一显示状态；不增加平行
导航 route 或第二环境变量 owner。网络设置面使用返回按钮、标题、状态刷新按钮和可滚动内容，
分成以下未嵌套区域：

1. **状态**：当前全局模式、内嵌核心状态、系统 VPN 状态、最近错误、上次订阅更新时间。
2. **全局模式**：`直连 / 外部代理 / 内嵌订阅` 单选分段控件。
3. **外部代理**：主机、端口、可选用户名、可选密码、测试 URL 和“测试连接”。
4. **内嵌订阅**：订阅 URL、更新订阅、粘贴 YAML、导入 YAML、节点选择、启动/停止、测试。
5. **网络边界**：局域网地址是否经代理；系统 VPN 下是否允许启动内嵌核心。
6. **逐脚本规则**：搜索框、已导入传统脚本列表，以及每项的
   `继承 / 直连 / 外部代理 / 内嵌订阅` 菜单。

密码和订阅 URL 默认遮蔽，用户可用眼睛图标临时显示。页面不显示完整 YAML、节点密码、UUID、
控制面 secret 或本地代理认证；粘贴 YAML 使用单独编辑对话框，保存后只显示配置摘要。
“加载节点”和“测试”允许使用当前草稿；若用户随后确认放弃修改，本次草稿启动或切换的内嵌核心
会先停止再退出，已保存密文配置不会删除。未修改配置时正常关闭页面不会停止已保存配置使用的核心。

## 4. 用户需要填写的内容

### 4.1 外部 HTTP 代理

适用于手机已运行 Clash、局域网网关或其他 HTTP CONNECT 代理。字段为：

| 字段 | 必填 | 规则 | Clash 示例 |
| --- | --- | --- | --- |
| 主机 | 是 | DNS 名、IPv4 或 IPv6；不带 scheme/path | `127.0.0.1` |
| 端口 | 是 | `1..65535` | Clash `mixed-port` 常见为 `7890` |
| 用户名 | 否 | 与密码同时为空或同时填写 | Clash 开启入口认证时填写 |
| 密码 | 否 | 与用户名同时为空或同时填写 | Clash 开启入口认证时填写 |
| 测试 URL | 是 | `http` 或 `https` | `https://cp.cloudflare.com/generate_204` |

第一版外部入口明确使用 HTTP/HTTPS CONNECT。Clash 的 `mixed-port` 同时接受 HTTP，用户无需
改填 SOCKS 端口。Kiyori 不修改 Android 系统代理，也不会自动扫描端口。

### 4.2 内嵌 Clash 订阅

用户任选一种配置来源：

- 订阅 URL：直接粘贴服务商给 Clash/Mihomo 的 `http://` 或 `https://` YAML 订阅地址；
- 粘贴 YAML：粘贴完整 Clash/Mihomo YAML；
- 导入 YAML：从系统文件选择器读取 `.yaml` 或 `.yml`。

导入上限为 4 MiB、UTF-8、单个 YAML document。URL 更新使用固定 Mihomo User-Agent、15 秒连接
和 30 秒读取超时，最多读取 4 MiB；HTTP URL 会在 UI 标记明文风险。更新只在用户点击时执行，
先下载、解析、清洗并验证，全部成功后才原子替换当前配置。失败不会改动现有配置，也不会改变
任何脚本路由。

## 5. 路由模型与优先级

```text
GlobalMode  = DIRECT | EXTERNAL_PROXY | EMBEDDED_SUBSCRIPTION
ScriptMode  = INHERIT | DIRECT | EXTERNAL_PROXY | EMBEDDED_SUBSCRIPTION
Effective   = ScriptMode == INHERIT ? GlobalMode : ScriptMode
```

- 初始值是全局 `DIRECT`，所有脚本 `INHERIT`。
- 模式在每个网络调用开始时形成不可变快照；请求途中修改设置只影响后续调用。
- 脚本不能通过参数、环境变量或 YAML 改写 effective mode。
- 选择 `EXTERNAL_PROXY` 但外部配置无效、选择 `EMBEDDED_SUBSCRIPTION` 但配置/节点/核心无效时，
  该调用明确失败。绝不改走直连或另一代理。
- 停止内嵌核心后，仍选择内嵌模式的下一次调用会按已保存配置重新启动；“停止”只结束当前进程，
  “直连”或修改策略才改变路由。
- 删除脚本时保留其 mode override，重装同 package ID 后继续生效；设置页只展示当前已导入的传统
  脚本，并提供“恢复全部继承”确认操作。

典型配置：

- 只让 Google 和 Brave 走内嵌订阅：全局选直连，这两个脚本选内嵌订阅。
- 除国内脚本外全部走内嵌订阅：全局选内嵌订阅，国内脚本逐项选直连。
- 手机 Clash 只供部分脚本使用：配置 `127.0.0.1:7890` 外部代理，目标脚本选外部代理。

## 6. Android 系统 VPN / Clash 共存

| Kiyori effective mode | 系统 VPN 关闭 | 系统 Clash/VPN 开启 |
| --- | --- | --- |
| `DIRECT` | Kiyori 不增加应用代理 | 仍受 Android VPN 捕获；“直连”不等于绕过系统 VPN |
| `EXTERNAL_PROXY` | 连接用户填写的 HTTP 代理 | 仍连接该代理；若为 Clash 本机端口，由 Clash 完成出站 |
| `EMBEDDED_SUBSCRIPTION` | Kiyori -> 本机 mihomo -> 选中节点 | mihomo 出站也可能被系统 VPN 捕获，形成嵌套链路 |

系统 VPN 通过 `ConnectivityManager` 的 `TRANSPORT_VPN` 检测。内嵌模式默认在检测到 VPN 时拒绝
启动，并显示“系统 VPN 会形成嵌套代理”；只有用户显式开启“允许系统 VPN 下运行内嵌代理”后
才能启动。外部模式不启动内嵌核心。Kiyori 不尝试识别 VPN 品牌、不关闭 Clash、不绑定底层物理
网络，也不隐式替用户选择模式。

## 7. 内嵌 mihomo 核心

### 7.1 固定制品

- 上游：`MetaCubeX/mihomo`
- tag：`v1.19.30`
- commit：`ac017cdd246ce8bd547653d927e7bf77d7ee73d5`
- release asset：`mihomo-android-arm64-v8-v1.19.30.gz`
- 压缩大小：`17,932,346` bytes
- 压缩 SHA-256：`19AFEB40FCA190FC2E3906A4E3B87C74A0C2120626FD3CB3AE0CF4092CB780AD`
- 解压大小：`52,586,488` bytes
- ELF SHA-256：`94344144936968F25E7089BBEAC2D87F3CAF67574BA433511424724AD7435DAD`
- 目标：ELF64/AArch64/PIE，解释器 `/system/bin/linker64`，全部 `PT_LOAD >= 0x4000`
- 许可证：GPL-3.0；Kiyori 的开放源代码许可页和第三方制品文档登记上游、tag、commit、来源和许可。

Git 不提交 52 MiB ELF。Gradle 生成任务从固定 HTTPS release URL 下载到机器缓存，校验压缩大小
和 SHA-256，再解压到 variant 的生成式 `jniLibs/arm64-v8a/libkiyori_mihomo.so`，并校验 ELF
大小、SHA-256、ABI、解释器和 16 KiB segment alignment。无有效缓存且下载失败时构建明确失败。

### 7.2 进程与端口

1. `ScriptProxyRuntime` 是 application-context 单例，使用互斥状态机管理唯一 mihomo 进程。
2. 每次启动分配仅监听 `127.0.0.1` 的动态 mixed port 和动态 Controller port；端口只在写配置
   前短暂探测，随后以 Controller readiness 和进程存活共同确认启动结果。
3. 每次启动生成新的本地代理用户名、密码和 Controller secret，均为 128-bit 以上随机值，只在
   内存与本次运行配置中存在。
4. 通过生成式 `libkiyori_mihomo_launcher.so` 启动。launcher 在 exec 前设置 Linux
   `PR_SET_PDEATHSIG=SIGTERM`，保证 Kiyori Java 进程死亡时核心收到终止信号。
5. 启动参数固定为 `-d <runtime-dir> -f <generated-config> -ext-ctl 127.0.0.1:<port>
   -secret <secret>`。不接受订阅提供的命令、环境变量或 post-up/post-down。
6. readiness 必须通过带 Bearer secret 的 Controller `/version`，再把选中节点写入
   `/proxies/KIYORI_SCRIPT_PROXY`；两步完成前不向脚本返回代理 endpoint。
7. 配置修改、显式停止或致命进程退出会终止进程；日志不记录订阅、认证、节点凭据或完整配置。
   Controller 就绪并读取节点后立即删除明文主配置，正常停止清空 runtime 目录；脚本宿主初始化时
   异步清除强杀遗留，并让任何新核心启动先等待该清理完成。

运行状态为：

```text
STOPPED -> STARTING -> AWAITING_SELECTION | RUNNING
STARTING | AWAITING_SELECTION | RUNNING -> STOPPING -> STOPPED
STARTING | RUNNING -> ERROR
```

`AWAITING_SELECTION` 只用于首次读取 provider 节点清单，不能给脚本提供 endpoint。进程异常退出
会使正在使用内嵌路由的调用明确失败，并在 UI 保留经过脱敏的错误摘要。

## 8. 订阅清洗与节点选择

YAML 使用 `snakeyaml-engine 3.1.1` 的 safe load/dump，限制别名、嵌套深度和总代码点。Kiyori 不把
原 YAML 直接交给核心，而是构造新 root：

```yaml
mixed-port: <dynamic>
allow-lan: false
bind-address: 127.0.0.1
authentication:
  - <ephemeral-user>:<ephemeral-password>
mode: rule
log-level: warning
external-controller: 127.0.0.1:<dynamic>
secret: <ephemeral-secret>
proxies: <validated outbound proxies>
proxy-providers: <validated HTTP providers with rewritten cache paths>
proxy-groups:
  - name: KIYORI_SCRIPT_PROXY
    type: select
    proxies: <raw proxy names>
    use: <provider names>
rules:
  - MATCH,KIYORI_SCRIPT_PROXY
```

清洗规则：

- 只保留出站 `proxies` 和 HTTP `proxy-providers`；provider `path` 重写到本次私有 runtime 目录。
- 可保留不含 listener 的 DNS 结构；删除 `listen`。若订阅没有 DNS，使用 mihomo 默认解析行为。
- 删除 `port`、`socks-port`、`redir-port`、`tproxy-port`、原 `mixed-port`、`listeners`、`tun`、
  `iptables`、`tunnels`、`ss-config`、`vmess-config`、全部原 Controller/UI/CORS/secret、NTP、
  geodata 自动更新、rule providers、rules、sub-rules、profile 和脚本钩子。
- 拒绝名称为空/重复、名称 `KIYORI_SCRIPT_PROXY` 冲突、无代理且无 provider、provider 非 HTTP、
  provider URL 非 HTTP(S)、节点 server 为 loopback/unspecified/multicast，以及无法被 mihomo
  `-t` 验证的配置。
- 节点选择保存名称，不保存序号。更新后若当前名称在静态节点中消失则清空选择；provider 节点在
  启动后由 Controller 验证。名称无效时状态保持不可用，用户必须重新选择。

设置页“加载节点”可以启动到 `AWAITING_SELECTION`，通过 Controller 读取
`KIYORI_SCRIPT_PROXY` 的 `all` 列表；用户选择后用 Controller `PUT` 固定节点。脚本请求不会在
节点未确认时穿过组的默认第一项。

## 9. 敏感数据与持久化

`ScriptNetworkConfigStore` 的唯一文件位于：

`context.noBackupFilesDir/script_network/config.v1.enc`

- Android Keystore alias：`kiyori_script_network_v1`
- 算法：AES-256/GCM/NoPadding，每次写入随机 96-bit IV，GCM tag 128-bit
- 文件头包含固定 magic、schema version 和 IV；payload 是 Kotlin serialization JSON
- 使用 `AtomicFile` 写入，成功完成后才发布新的 `StateFlow` 快照
- 订阅 URL、YAML、外部代理密码、节点字段和本地测试配置都在同一密文中
- 明文生成配置只存在于 no-backup 私有 runtime 目录，权限收紧为 app UID，停止后删除
- 不进入 `EnvPreferences`、普通 SharedPreferences、日志、崩溃报告、备份、导出、脚本参数或 Git

Keystore key 丢失、密文损坏或 schema 不受支持时，不删除原文件、不猜测配置。设置页显示不可读
状态，用户可在二次确认后执行“重置代理设置”，删除该密文与 alias 并恢复初始直连。

## 10. HTTP、地址与失败语义

### 10.1 请求路由

- HTTP 代理使用 OkHttp `ProxySelector`，HTTPS 通过 CONNECT；需要认证时使用请求级
  `proxyAuthenticator`，不修改 JVM 全局 Authenticator。
- 内嵌 endpoint 始终带本次随机认证；外部 endpoint 使用用户配置认证。
- `localhost`、`127.0.0.0/8`、`::1` 和 Kiyori 本地服务固定直连，避免代理环路。
- RFC1918、link-local、ULA 等局域网地址默认直连。用户开启“局域网地址也使用代理”后这些地址
  才走 effective proxy；loopback 仍固定直连。
- 代理模式下目标 DNS 由 HTTP proxy/mihomo 处理；Kiyori 只需解析代理主机。直连和固定本地地址
  遵守 Android 当前网络与 DNS。
- TLS 验证保持 OkHttp 默认信任和 hostname verification；脚本现有 `ignore_ssl=true` 继续明确
  拒绝，不为代理开放不安全证书模式。

### 10.2 入口行为

- `http_request` 的普通与 streaming 路径共用同一路由快照。
- `multipart_request` 对文件路径、body 和 headers 的现有校验不变。
- 脚本 `visit_web` 最多读取 8 MiB HTML，解析最终 URL、标题、常用 meta、可见文本、绝对链接
  和可下载 HTTP(S) 图片；大文本继续按现有 12,000/8,000 字符合同写入 clean-on-exit 文件。
- 脚本 `download_file` 流式写入同目录临时文件，成功后原子替换目标；失败删除本次临时文件，
  不留下看似成功的部分目标。单次下载使用一个 route snapshot。

错误必须包含稳定分类和可操作摘要：`CONFIG_MISSING`、`CONFIG_INVALID`、`VPN_CONFLICT`、
`CORE_MISSING`、`CORE_START_FAILED`、`NODE_NOT_SELECTED`、`PROXY_CONNECT_FAILED`、
`SUBSCRIPTION_FAILED`、`HTTP_FAILED`。用户可见错误不含密码、URL query、节点凭据、YAML 或 secret。

## 11. 代码所有权与计划文件

| 所有者 | 计划位置 | 职责 |
| --- | --- | --- |
| 模型/策略 | `core/tools/javascript/network/ScriptNetworkModels.kt` | mode、配置、状态、错误、纯策略 |
| 加密存储 | `.../ScriptNetworkConfigStore.kt` | Keystore、AtomicFile、StateFlow |
| YAML | `.../MihomoConfigSanitizer.kt` | safe parse、清洗、摘要、生成配置 |
| 核心 | `.../ScriptProxyRuntime.kt` | process、Controller、节点、状态、VPN gate |
| HTTP | `.../ScriptNetworkHttpClientFactory.kt` | route snapshot、ProxySelector、认证、测试 |
| 身份 | `JsToolManager.kt`、`JsEngine.kt`、`JsInitRuntimeScriptBuilder.kt`、`JsNativeInterfaceDelegates.kt` | 可信 package ID 传播 |
| 工具接入 | `StandardHttpTools.kt`、`StandardWebVisitTool.kt`、`StandardFileSystemTools.kt` | 四入口统一消费策略 |
| 设置 UI | `PackageManagerScreen.kt`、新增 `ScriptNetworkSettingsScreen.kt` | 唯一 UI owner、导入、测试、逐脚本策略 |
| Native | `tools/mihomo_parent_launcher/native-lib.cpp`、`app/build.gradle.kts` | 固定制品、父死亡信号、生成式 JNI |
| 文档 | `README.md`、`CONTEXT.md`、开放源代码许可、本文 | 用户操作、语义、来源与状态 |

## 12. 串行里程碑

1. **M0 设计冻结**
   - [DONE] 核对 UI owner、脚本身份、四个网络入口和 WebView 影响面
   - [DONE] 从官方 tag 复核 mihomo CLI、配置、认证和 Controller selector API
   - [DONE] 冻结模式、设置字段、VPN 共存、敏感存储、清洗和验收矩阵
2. **M1 模型、存储与清洗**
   - [DONE] 实现纯模型、配置校验、逐脚本优先级和 VPN 快照
   - [DONE] 实现 Keystore/AtomicFile 存储和显式重置
   - [DONE] 实现 YAML safe load、清洗、摘要和生成配置测试
3. **M2 可复现核心**
   - [DONE] 实现固定 release 下载/缓存/哈希/ELF 校验和生成式 JNI
   - [DONE] 实现 parent-death launcher、runtime 状态机、Controller 和节点选择
4. **M3 四入口接入**
   - [DONE] 传播宿主可信的传统脚本身份并删除脚本伪造值
   - [DONE] 接入 HTTP、stream、multipart、visit 和 download
   - [DONE] 以调用链和静态契约证明普通 AI/Browser/ToolPkg/MCP 等调用不读取该策略
5. **M4 设置 UI**
   - [DONE] 设置选择层、全屏代理设置、三模式、字段校验和状态
   - [DONE] URL/YAML/文件导入、节点选择、草稿测试、VPN 冲突和逐脚本规则
6. **M5 文档与本地验证**
   - [DONE] 更新 `README.md`、`README.en.md`、`CONTEXT.md`、许可和构建恢复说明
   - [DONE] 专项与全量 JVM、Python、资源、架构、正式门禁和差异检查通过
   - [DONE] Debug APK 构建通过；核心/launcher ELF、ABI、V2 单签名和 16 KiB 对齐通过
7. **M6 Git 交付**
   - [PENDING] 审计允许清单、敏感内容、生成物、异常大文件、子模块和远端竞争
   - [PENDING] 提交并推送 `main`，核对 local/tracking/remote ref

## 13. 验收矩阵

### 13.1 自动验证

- 全局三模式与逐脚本四模式的全部组合，配置缺失时明确失败。
- 脚本不能伪造/覆盖内部 package ID；并行的四个 `JsEngine` 不串脚本身份。
- HTTP、stream、multipart、visit、download 在 direct/external/embedded 路由下取得同一策略。
- 普通 AI `http_request/visit_web/download_file` 不带脚本身份，行为不变。
- Browser `browser_*`、userscript、ToolPkg、MCP、Provider、播放器和浏览器下载代码不读取该 store。
- YAML listener/TUN/Controller/脚本钩子被删除；恶意路径、过大/深层/别名炸弹、重复节点被拒绝。
- 配置密文不含可检索的 URL、密码或节点凭据；失败日志完成脱敏。
- VPN gate、loopback 固定直连、LAN 开关和无隐式路由切换。
- core 下载 SHA、解压 SHA、ELF64/AArch64/PIE、依赖、解释器和 `PT_LOAD >= 0x4000`。

### 13.2 Debug APK

- `app/build/outputs/apk/debug/app-debug.apk` 存在且为本轮新产物。
- 只包含一个 `lib/arm64-v8a/libkiyori_mihomo.so` 和一个 parent-death launcher，无重复 basename。
- mihomo bytes 与固定 SHA 相同；launcher 为 ELF64/AArch64 且 16 KiB 对齐。
- 包身份、唯一 Launcher、Android Debug V2 单 signer 和 `zipalign -P 16` 通过。

### 13.3 真机待验收

- 未开启系统代理/VPN时，Google、DuckDuckGo、Brave 与一个非 Search HTTP/下载脚本分别使用
  内嵌订阅成功。
- 全局直连加两个脚本 override，以及全局内嵌加一个脚本直连，流量符合设置。
- 手机 Clash 的 `127.0.0.1:mixed-port` 外部模式成功；端口关闭时明确失败。
- 系统 VPN 开启时内嵌默认阻止；用户确认后显示嵌套状态并按设置运行。
- Browser、AI Provider、播放器、MCP 和普通 AI `visit_web` 的流量与界面不受脚本策略影响。
- 旋转、后台/前台、进程强杀、订阅更新、节点失效、错误密码和核心异常退出均有稳定状态。

自动测试和本地 APK 不能替代以上设备流量与视觉证据，完成 Git 交付后仍保持
`DEVICE VERIFICATION PENDING`，直到目标设备逐项通过。
