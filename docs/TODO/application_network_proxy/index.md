---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
baseline: 485cf3f7a8a1c2e5ccf0d525baf960ae8398f9b5
date: 2026-08-23
---

# Kiyori 应用级网络代理与内嵌 Mihomo

## 2026-08-28 最新安装回归修复

状态：`LOCAL FIX VERIFIED / DEVICE VERIFICATION PENDING`。

最新安装报告中的代理失败不是 Mihomo 或订阅故障：Mihomo 已报告 `controllerStatus=200`、
`mixedPortListening=true`，但应用在真实 Browser WebView provider/support-library bridge 完成前就调用了
进程级 `ProxyController.setProxyOverride`。现在 `KiyoriApplication` 仍只负责登记启动协调，
`KiyoriNetworkProxyManager` 先建立可等待的 readiness，再由首个 Browser `WebView` 完成配置、能力探测和用户脚本
桥接后调用 `notifyBrowserWebViewRuntimeReady()`，启动协调随后才安装 WebView 代理。首次远程主文档继续等待同一
readiness，失败仍明确阻止导航，不静默直连，也不增加第二 WebView、第二 Mihomo 或重试路径。

同一安装中的脚本长按崩溃来自扩展页直接组合 `KiyoriSettingsSelectionSheet` 时缺少
`LocalKiyoriSettingsColors` 提供者。选择抽屉组件现在在自身边界建立 `KiyoriSettingsTheme`，所以设置路由和扩展页
长按使用同一主题合同，未改变 `scriptModes` 的唯一状态 owner。

本轮 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`503669293` 字节，SHA-256
`86944F0B9A578B3CBA67BCBC18CBC5FB3DBD355BACDD85D8139CAC05BFAC2D0F`；`com.kiyori / 45 / 0.1.0`，Debug V2 签名、
16 KiB ZIP 对齐、唯一 launcher、脚本代理运行时和播放器运行时打包门禁均通过。目标 Android 设备尚未安装验收，
状态保持 `verification_pending`。

## 目标与完成标准

本阶段把上一版“传统脚本宿主代理”提升为 Kiyori 唯一的应用级网络路由能力。用户只在
“设置首页 -> 更多功能 -> 网络代理”管理订阅、内嵌核心、代理模式和脚本细分规则。Browser、播放器、
AI 主模型与其他由 Kiyori 持有的联网客户端读取同一份配置，不再
由脚本页面持有第二套代理状态。

完成标准：

1. Clash/Mihomo 订阅可以通过 URL 或本地 YAML 导入到加密订阅库；订阅库支持多条保存、刷新
   状态、远端更新、编辑、复制、切换和删除。服务端按客户端类型返回 Clash YAML 时必须获得
   mapping 根，而不是 Base64 通用 URI 列表。
2. 每份订阅独立保留安全、可用的 `proxy-groups`、静态节点、HTTP provider、策略选择和最近测速
   结果；页面按订阅和策略组展示节点，可执行单节点和整组测速。
3. 内嵌核心关闭时，Kiyori 在应用层不设置代理；开启时，顶层代理模式统一决定请求是否进入
   内嵌 Mihomo。传统脚本继续允许按脚本包名细分。
4. AI 服务、AI 工具、Browser、下载器、播放器、脚本与扩展、Kiyori 在线服务的受支持宿主
   网络入口全部接入同一个路由 owner；代理错误必须终止当前请求，不得静默改走另一条路线。
5. 当前订阅是唯一运行配置来源。切换、更新或编辑 URL 完成下载、清洗和 `mihomo -t` 校验后才
   原子保存；运行应用失败必须明确区分于保存失败，不自动切回旧订阅。
6. 外部 Clash/VPN 与内嵌 Mihomo 的关系有确定、可测试的行为；“直连”只表示不经过 Kiyori
   Mihomo，不能承诺绕过 Android 系统 VPN。
7. 新页面复用 Kiyori 设置视觉和唯一 route stack；脚本顶栏设置按钮直接打开环境变量抽屉，
   抽屉底部左侧“网络代理”按钮进入同一个设置子页面。
8. 敏感配置、运行时明文、核心制品和私有订阅都满足现有安全、打包、许可证和仓库卫生门禁。

## 2026-08-28 规则管理与站点代理禁用方案

状态：`IMPLEMENTED LOCALLY / AUTOMATED VALIDATION PENDING / DEVICE VERIFICATION PENDING`。

### 研究结论

- 当前规则页把订阅规则截断为最多 200 行的只读文本，订阅规则没有编辑事务；自定义规则只支持
  `DOMAIN`、`DOMAIN-SUFFIX`、`DOMAIN-KEYWORD`，因此 IP 规则不能由用户创建。
- 订阅清洗器只接受 10 种规则类型，并用简单的 `split(',')` 解析逻辑规则；Mihomo 官方规则语法还包括
  `DOMAIN-WILDCARD`、`DOMAIN-REGEX`、`GEOSITE`、`IP-SUFFIX`、`IP-ASN`、`SRC-*`、`IN-*`、
  `PROCESS-*`、`UID`、`NETWORK`、`DSCP`、`RULE-SET`、`AND/OR/NOT`、`SUB-RULE` 和 `MATCH`。
  规则顺序是从上到下，前面的规则优先级更高；`no-resolve`、`src` 等附加参数必须保留。
- 现有 `sanitizedYaml` 是订阅运行配置的唯一来源，因此编辑订阅规则必须在同一 manager 事务中同时更新
  `sanitizedYaml` 和 `subscription.rules`，运行中的 active subscription 也必须重新协调；更新 URL 或重新导入
  本地 YAML 时整份订阅重新清洗，覆盖此前对订阅规则的编辑，自定义规则不受影响。
- 浏览器站点设置已经有同域名持久化规则和统一 Back 链，但网络代理不在该特征集合中。WebView 走进程级
  `ProxyController`，浏览器的 OkHttp/下载入口走 `KiyoriNetworkProxyManager`，必须让两条路径都读取同一
  站点规则，而不能再建第二份域名配置。

### 选择的设计

1. 规则管理采用单页两段式布局：顶部搜索框；“自定义规则”永远位于上方，“当前订阅规则”位于下方；
   两段都在同一滚动列表中按需渲染，搜索同时匹配规则类型、匹配值、目标和完整原文。订阅规则行点击进入
   原文编辑对话框，保留完整 Mihomo 语法和附加参数；自定义规则继续提供新增、编辑、启用/停用和删除。
2. 订阅规则以原始规范化字符串按索引编辑，保存前使用与导入相同的 YAML 规则校验和目标校验；不接受空行、
   控制字符、重复规则或超出长度/数量上限的输入。规则解析从首个逗号和最后一个逗号取类型/目标，中间内容
   可包含逻辑规则所需的逗号和括号。支持的类型集合与 Mihomo 文档一致；`RULE-SET` 只有在订阅提供对应
   `rule-providers` 时才保留，provider URL/path 经过同一安全清洗；`SUB-RULE` 会连同顶层
   `sub-rules` 映射一起校验并保留，缺失依赖继续计数并拒绝进入运行 YAML。
3. 自定义规则新增 `IP-CIDR`、`IP-CIDR6`、`IP-SUFFIX`、`IP-ASN`、`SRC-IP-CIDR`、`SRC-IP-SUFFIX`、
   `SRC-IP-ASN`、`GEOIP` 和 `SRC-GEOIP` 选项；运行时生成对应 Mihomo 规则，代理目标仍为
   `KIYORI_APP_PROXY`，直连目标仍为 `DIRECT`。规则排序保持现有特异性顺序，并让所有自定义规则整体先于订阅规则。
4. “网站配置”新增 `禁用网络代理`，作为一个域名的最高代理禁用优先级。该开关写入现有
   `WebSessionSiteSettingsRule`，覆盖 Kiyori 应用代理、订阅规则和脚本代理，但不绕过 Android 系统 VPN。
   `KiyoriNetworkProxyManager` 接收 Browser 设置 store 的只读域名判定投影：动态 ProxySelector 按 host 判定，
   `ProxyController` 更新 bypass 列表；变更后复用当前 Mihomo runtime 重新安装同一个 process-wide override，不创建
   第二核心、第二 WebView 或静默直连路径。关闭站点开关后同一设置入口即可恢复全局代理。

### 迁移、风险与回滚点

- 规则模型继续使用现有 schema-5 字段，新增类型枚举保持 Kotlin serialization 的旧值可读；不引入第二份规则
  存储。订阅编辑不改变订阅 ID、节点选择或测速记录，更新/重新导入时按现有原子替换边界覆盖规则。
- 规则 provider 的远程拉取由 Mihomo 运行时负责，若 provider 本身不可用，核心启动会按现有明确错误路径失败；
  不添加网络失败回退、自动换节点或静默直连。站点代理禁用只影响 Browser 相关 host 判定，其他模块继续使用各自
  的应用级路由。
- 回滚点为本次提交前的 `main`；若设备验证发现 provider 兼容性问题，可独立回滚 provider 保留改动，不影响
  自定义 IP 规则与站点开关的持久化格式。

### 验证矩阵

- JVM：规则类型/逻辑逗号/附加参数/目标校验、provider 清洗、订阅规则编辑覆盖、IP 自定义规则生成、站点规则
  持久化与最高优先级判定、ProxySelector/ProxyController bypass 投影。
- 静态与构建：相关 `:app:testDebugUnitTest`、Python 网络代理合同、formal readiness、`git diff --check`，
  串行 `:app:assembleDebug --no-daemon --console=plain` 和 APK 身份/签名/16 KiB 对齐审计。
- 设备：安装后检查规则搜索与编辑、订阅更新覆盖、自定义 IP 规则、站点开关对顶层页面/子资源/脚本/下载的
  实际路由，并验证切换开关后 WebView 代理立即恢复；设备未验收前状态保持 `verification_pending`。

## 当前实施状态

## 2026-08-27 在线播放、启动代理与脚本规则重构方案

状态：`IMPLEMENTATION VERIFIED LOCALLY / AUTOMATED VALIDATION AND DEBUG APK AUDIT COMPLETE / DEVICE VERIFICATION PENDING`。

本轮基线为 `main@acfaa288`，工作树在研究开始时干净，Kiyori 当前仍未公开发行，因此以下用户可见
接口直接收敛到新设计；不会保留一套并行的旧页面或旧状态 owner。现有 `com.kiyori.platform.network`
仍是唯一代理 owner，`PlayerSession` 仍是唯一播放器 owner，Browser 与 AI 继续共用同一个
`StandardBrowserSessionTools`/WebSession/WebView。

### 已确认根因

1. 嗅探视频全屏错误来自 `PlayerSurfaceLeasePolicy.requestFullscreenActivityLaunchIfReady()` 的
   严格断言。`BrowserPlayerSupport.openMediaCandidate()` 在 `PlayerSession.open()` 后无条件调用
   `requestFullscreenActivityLaunchWhenReady()`；同一个 request 的重复嗅探回调、展示切换或关闭/重建
   交错时，Surface lease 可能已经没有待处理的全屏 target，内部 `check` 直接把
   `Fullscreen Activity cannot launch without a pending fullscreen Surface` 暴露到下方错误弹窗。
   这不是媒体 URL、mpv 解码或网络失败。
2. 启动代理存在可观察的初始化空窗：`KiyoriApplication.onCreate()` 仅异步调用
   `reconcileEnabledState()`，而 Browser 可以在 Mihomo runtime 与 AndroidX `ProxyController` 完成前
   创建 WebView 并开始导航。设置页重新测速会再次协调，因而表现为“返回后才生效”。这属于启动时序
   和共享完成状态缺失，不通过增加第二核心、系统 VPN 或静默直连处理。
3. 当前脚本页仍允许手动输入包名，并且只展示已启用脚本；这会使配置状态与真实安装清单分裂。扩展页的
   脚本项尚未把短按和长按建模为两个明确动作，长按仍会复用详情弹窗。

### 目标合同与实现边界

**播放器全屏**

- `requestFullscreenActivityLaunchWhenReady()` 改为幂等状态投影：已有启动请求直接复用；当前已经是
  全屏 owner 时不重新发请求；仅在 `FULLSCREEN` pending target、无活动旧 owner 且 native detach 已完成
  时创建一次 request。过期/重复调用返回“无动作”而不是抛用户可见异常。
- `BrowserPlayerSupport` 只在 `PlayerSession.open()` 接受了目标 request/presentation 后请求启动；同一
  request 的展示切换先由 `PlayerSession` 完成租约迁移。旧 document token 的候选仍由 Browser owner
  拒绝，不能重新打开播放器。
- 保持 `init -> Surface attach ACK -> loadfile` 顺序、唯一 `PlayerSession`、唯一 `:player` 进程和
  最近的 floating/fullscreen 生命周期修复；不增加播放器实例或媒体重试回退。

**启动代理**

- `KiyoriNetworkProxyManager` 增加进程级启动协调的共享 readiness 状态和 generation，`onCreate()` 只启动一次
  初始协调任务；后续 Browser 首次 attach/navigation 等待当前协调尝试。失败会明确阻止远程导航，但设置变更或
  重新协调成功后发布新的 generation，不能被第一次失败永久封死。协调仍在现有 `mutationMutex` 内读取最新加密配置、
  启动/复用 Mihomo 并完成 WebView `ProxyController` 安装。
- 代理关闭、直连模式、无有效订阅、VPN 冲突和明确的启动错误保持可观察失败；不在网络失败时切换节点、
  绕过应用代理或吞掉异常。启动等待只解决时序，不改变路由语义。
- `KiyoriNetworkProxyConfigStore` 的跨进程文件指纹重读继续保留；Browser WebView 使用安装完成后的
  process-wide override，不创建第二 WebView 或第二代理状态。

**代理设置与脚本规则**

- 删除用户可见的“模块连接模式”入口及 `KiyoriNetworkProxyConfig.moduleModes` 语义。schema 5 读取旧
  schema 4 时丢弃未发布的 `moduleModes`，原样保留顶层 `defaultMode` 与 `scriptModes`，写回后不再生成
  `moduleModes`；未发布产品不保留旧页面兼容入口。`KiyoriNetworkModule` 枚举继续作为代码路由分类，
  不等于用户设置项。
- “逐脚本连接模式”统一改名为“脚本规则”，包括主页入口、子页标题、空状态、返回链和说明文案；保留
  `scriptModes` 的 `INHERIT/DIRECT/PROXY` 三态和同一 manager 写入路径。
- 脚本清单只来自实际已安装的传统 JsEngine 包发现器，按 AI 对话 -> 左抽屉 -> 扩展 -> 脚本的分组/排序
  规则投影；启用与否不影响识别。删除手动包名输入和“添加规则”，顶部“刷新脚本”移为标题行右侧
  单独的刷新图标按钮，刷新只重新读取安装清单。
- 扩展脚本短按保持现有脚本详情/执行入口；长按打开代理规则底部抽屉，选项与网络代理页共享同一
  `scriptModes` 状态和 `KiyoriNetworkOverrideMode` 文案。抽屉关闭、返回和配置保存均不改变脚本详情
  route stack。

### schema、文件与验证矩阵

实现预计涉及：`KiyoriNetworkProxyModels.kt`、`KiyoriNetworkProxyConfigStore.kt`、
`KiyoriNetworkProxyManager.kt`、`KiyoriApplication.kt`、`KiyoriNetworkProxySettingsPage.kt`、
传统脚本发现/扩展脚本列表的实际 owner、`BrowserPlayerSupport.kt`、`PlayerSession.kt`、
`PlayerSurfaceLeasePolicy.kt`、`WebSessionBrowserScreen.kt`、资源文案、`CONTEXT.md`、`README.md` 和
本 TODO；不修改无关市场、下载或第二浏览器实现。

自动验证至少覆盖：

- 全屏启动：无 target、已有 fullscreen owner、重复 request、floating detach ACK 后 request、同一
  request 重复嗅探、关闭中/旧 document 候选和 Activity 重建；断言不再出现该 `IllegalStateException`。
- 启动代理：默认 `enabled=true` 且存在有效订阅时，首次 manager 协调完成前的 Browser attach 会等待；
  协调后 `ProxyController` 和 runtime generation 可观察；直连/无订阅/VPN 冲突错误不被吞掉。
- 脚本规则：旧 schema 读取迁移、写回不含模块模式、全部已安装脚本（含停用项）投影、空清单、刷新、
  文案和短按/长按 action 分离；脚本规则与网络代理页读写同一 map。
- 本地门禁：聚焦及完整 JVM 测试、`check_formal_readiness.py --require-main`、Python 网络代理合同、必要的 architecture/Markdown
  检查、`git diff --check`，以及串行 `:app:assembleDebug --no-daemon --console=plain` 和 APK 核验。
- 设备边界：真实嗅探 MP4/HLS、页面首次进入代理站点、前后台/旋转、脚本停用项发现和长按手势仍需安装
  APK 后在目标 Android 设备完成，自动测试不能替代，完成前状态保持 `verification_pending`。

### 本轮本地验证证据（2026-08-27）

- `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`，159 actionable tasks；包含播放器租约、浏览器启动
  导航策略、schema 2/3/4 -> 5 迁移和网络代理策略测试。
- `python -B -m unittest ci.test.test_application_network_proxy_contract`：11/11 通过。
- `python -B ci/script/check_formal_readiness.py --repository . --require-main`：PASS。
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`，235 actionable tasks；唯一
  launcher、脚本代理运行时和播放器运行时打包门禁通过。APK 为 `app/build/outputs/apk/debug/app-debug.apk`，
  包名/版本 `com.kiyori / 45 / 0.1.0`，Debug V2 单签名和 16 KiB ZIP 对齐通过。

> 下面带有 2026-08-24 及更早日期的段落是历史方案和当时的验证记录。其“模块连接模式”“逐脚本连接模式”
> 和手动包名入口已被本节 2026-08-27 schema 5 方案取代，不代表当前实现。

## 2026-08-24 规则模式网页与播放器链路修复

本轮根据 2026-08-24 现场导出的 Web/播放器/代理日志继续修复规则模式，而不是把所有失败归因
为上游节点。Mihomo 日志中的 `DOMAIN-KEYWORD`、`DOMAIN-SUFFIX` 直连命中伴随 DNS resolve
failed，根因是订阅的 `respect-rules: true` 让 DNS 解析重新进入业务规则图；清洗器现在明确启用
内部 DNS、关闭 `respect-rules`、关闭 IPv6、移除依赖域名启动的 `proxy-server-nameserver`，并在订阅
没有 DNS 段时写入受控的 IP nameserver。GeoSite/GeoIP 依赖继续被移除并计数，不会偷偷下载外部数据。

播放器日志中的 HTTPS 视频不能仅靠 mpv `http-proxy` 代理：mpv 该选项只覆盖 HTTP。`:player`
现在为需要应用级代理的 HTTP(S) 直链和 HLS 媒体创建一个随机令牌、仅监听 IPv4 `127.0.0.1` 的流式桥接端点；mpv
读取本地 HTTP 流，桥接端点用同一份 `PLAYER` 路由请求原始 URL，转发原始请求头、Range、响应状态、
Content-Range 和流式响应。桥接端点不会暴露在 LAN，也不创建第二个 Mihomo 或第二份持久化网络状态。
播放器诊断将该路径标记为 `PROXY_BRIDGE`；直连模式仍直接加载原始 URL。

自定义规则从“根据 `*.` 前缀猜测语义”升级为显式三种类型：完整域名 `DOMAIN`、域名后缀
`DOMAIN-SUFFIX`、域名关键字 `DOMAIN-KEYWORD`；规则动作仍为直连或代理。运行时按完整域名、后缀、
关键字的特异性排序，用户规则整体优先于订阅规则；schema 3 旧规则在读取时保留原有通配符含义并迁移
到 schema 4。订阅更新只替换订阅来源规则，用户规则和启用状态不被覆盖。

- [DONE LOCALLY] DNS 规则递归修复与无 DNS 段运行配置收口
- [DONE LOCALLY] HTTPS 播放器 loopback 流式桥接与 Range/请求头转发
- [DONE LOCALLY] 自定义规则显式类型、schema 迁移、排序和设置页选项
- [verification_pending] 目标设备使用真实订阅复测 WebView、HTTPS MP4/HLS、AI、下载与系统 VPN 并存

## 本轮规则模式设计（2026-08-24）

本轮将顶部“默认连接”更名为“代理模式”，提供固定顺序的三个选项：

1. **规则**：Kiyori 内嵌 Mihomo 接收请求后，先匹配用户自定义域名规则，再匹配当前订阅中
   已安全保留的直接规则，最后进入 `KIYORI_APP_PROXY` 策略组。用户规则优先于订阅规则，
   因而可以覆盖订阅的同域名结果。
2. **全局**：Kiyori 内嵌 Mihomo 接收的请求全部进入 `KIYORI_APP_PROXY`，不使用订阅域名规则。
3. **直连**：应用层不把请求交给 Kiyori Mihomo；Android 系统 VPN 仍可能继续生效。

这是应用范围的策略选择，只影响已经接入 `com.kiyori.platform.network` 的 Kiyori 请求，
不启用 `VpnService`，不影响其他应用。

模块连接模式与逐脚本连接模式继续保留，但它们只决定某个入口是否“跟随代理模式”或覆盖为
“代理/直连”。其中“代理”表示使用顶部当前模式，而不是另建一套规则核心；这样可以满足
Browser、AI、下载、播放器和传统脚本共享同一 Mihomo 规则，同时避开 AndroidX
`ProxyController` 的进程级限制。总开关关闭时所有入口均为直连。

“规则管理”位于“节点选择”与“订阅管理”下方，分为两类来源：

- **当前订阅规则**：从当前订阅 YAML 的 `rules` 中提取受支持的域名/IP/端口规则，只读展示；
  更新订阅会原子替换这部分内容。依赖外部 GeoSite、GeoIP、RULE-SET 文件的规则不会被伪装成
  可用规则，并在摘要中计数。
- **自定义规则**：单独加密保存在应用代理配置中，支持新增、编辑、删除和启用/停用；规则类型为
  完整域名、域名后缀或域名关键字，动作只有“直连”和“代理”。它们永远排在订阅规则前，订阅更新不会覆盖。

用户规则编译为 Mihomo `DOMAIN`、`DOMAIN-SUFFIX` 或 `DOMAIN-KEYWORD` 条目；代理动作指向
`KIYORI_APP_PROXY`，直连动作指向 `DIRECT`。每次规则、订阅、节点或顶部模式变化都会重新校验
并原子重建当前运行配置，失败时保留已保存配置并明确报告运行未生效。

### 本轮实施阶段

- [DONE LOCALLY] 数据模型、schema 迁移与规则校验
- [DONE LOCALLY] 订阅规则清洗、运行 YAML 合成与规则模式
- [DONE LOCALLY] Manager/Runtime 路由参数和更新事务
- [DONE LOCALLY] 设置页规则管理及模块/脚本文案
- [DONE LOCALLY] 定向测试、Debug APK 与文档
- [verification_pending] 真机网络、WebView、播放器、下载器、脚本和外部 VPN 并存验收

- `DONE LOCALLY`：用户真机确认订阅、策略组和节点可以正常导入展示，但主 runtime 与 probe runtime
  均在 `mihomo -t` 阶段拒绝运行配置，导致开启代理、选点和测速不可用。本轮已删除 DNS 中对外部
  GeoSite/GeoIP 数据文件和已移除 rule-provider 的残留依赖，并增加可查看、复制、导出和清空的脱敏代理日志。
- `DONE`：Android Keystore 随机 IV、保存事务和多订阅 schema 已修复；订阅 URL/YAML 导入、更新、
  切换、复制、删除、分组节点和单节点/整组测速均由同一代理 owner 持有。
- `DONE`：AI 主模型/语音/embedding、AI 工具、传统脚本、Browser、下载、播放器、Coil 图片和
  Kiyori 在线服务入口已接入统一网络模块；ToolPkg 与传统脚本身份边界已固定。
- `DONE`：设置入口、模块路由、订阅操作、分组节点、单项/整组测速、作用域进度与错误反馈均在
  同一页面 owner 内闭环；本轮新增设置会话浏览器往返修复、当前组/节点投影和已启用传统脚本发现。
- `DONE LOCALLY`：网络代理主页已收敛为“启用应用内代理、代理模式、节点选择、订阅管理、规则管理、模块连接模式、
  逐脚本连接模式、代理局域网地址、允许与系统 VPN 并存、重置网络代理”九个明确入口/控件；移除当前路由、
  当前订阅抽屉、策略组摘要、测试地址和布局选择等重复展示。
- `DONE LOCALLY`：节点选择作为独立子页面，只使用当前订阅的横向分组标签、分组内搜索、顶部整组测速和排序。
  节点按单列逐行显示，左侧无图标、标题允许多行完整展示，右侧测速按钮不触发选择；`select` 组点击行直接切换，
  自动策略组仅显示当前策略并禁止伪装手动选择。
- `DONE LOCALLY`：订阅管理作为独立子页面，顶部提供“添加订阅地址”和“导入 YAML 文件”两个按钮；订阅行点击
  立即切换当前订阅，右侧三点菜单仅提供“更新、编辑、复制、删除”。URL 更新与本地 YAML 重新导入继续由管理器
  完成下载/清洗/核心校验后的原子保存。
- `DONE LOCALLY`：模块连接模式和逐脚本连接模式均为独立子页面，保留真实模块/传统脚本发现、刷新脚本和手动
  包名规则；所有修改仍通过同一 `KiyoriNetworkProxyManager`。
- `REGRESSION FIXED LOCALLY`：崩溃报告 `9619e60b-3d09-4c69-b181-20b001d38251` 的设置路由
  生命周期已由 Shell 状态和 JVM 回归测试覆盖；上一份 APK 的现场保存问题不能作为当前实现结论。
- `verification_pending`：本轮修订 APK 尚未在真机验证；真实订阅、多订阅切换、日志交互、WebView、AI、播放器、
  下载器、脚本、外部 VPN 并存和进程生命周期矩阵仍待设备验收。
- `DONE LOCALLY`：完成应用级代理跨进程与跨入口修订。加密配置 Store 在读取时检查文件前缀指纹，
  因而主进程保存配置后，独立 `:player` 进程不会继续使用旧缓存；Browser 下载、AI 多段下载和显式
  `HttpURLConnection` 入口在任务开始时固定同一份路由快照，且 loopback/私网旁路与 OkHttp selector
  采用同一策略。播放器在初始化和每次媒体加载前应用 `PLAYER` 路由，并输出脱敏的应用级代理状态。
- `verification_pending`：2026-08-23 用户诊断报告显示 Android 与 `PlayerNetwork` 快照为
  `proxy=absent`，但 mpv 同时明确记录 `route=PROXY` 与 `mpvHttpProxy=127.0.0.1:<port>`；应用级
  路由已真实生效，TLS handshake aborted/loading failed 仍需区分 Mihomo runtime 生命周期与上游节点/Range
  链路问题。主进程代理日志还显示 Mihomo 在配置校验、Controller、节点切换和测速成功后以退出码 `0`
  输出 `shutting down`；主进程与独立 `:player` 进程的 runtime 必须按进程分别对齐 generation、端点和退出时间。

## 非目标

- 不启用 Android `VpnService`、Mihomo TUN、LAN listener、透明代理或设备全局代理。
- 不代理 Kiyori 之外的应用，不修改系统 Wi-Fi 代理，不接管其他应用的 Clash 配置。
- 不承诺改变 Terminal、用户自行启动的进程或远端 MCP 进程内部自行创建的 socket；只有 Kiyori
  宿主持有并能明确归类的网络入口进入本合同。
- 不把私有订阅 URL、订阅响应、节点密钥、Controller secret 或本地运行配置写入 Git、崩溃报告、
  任务日记或普通偏好文件。代理日志只保存当前进程内的有界脱敏事件；复制和导出必须由用户明确触发。
- 不安装 APK、不操作 ADB/模拟器/设备；真实网络、系统 VPN 和播放器流量仍需要目标设备验收。

## 现场错误与根因证据

2026-08-23 对用户提供的目标订阅只做结构化响应审计，未输出或持久化正文：

| 请求 User-Agent | HTTP | 响应结构 | 结果 |
| --- | --- | --- | --- |
| `Kiyori/0.1.0` | 200 | Base64 URI 列表 | 不能作为 YAML mapping 解析 |
| `mihomo/1.19.30` | 200 | Base64 URI 列表 | 不能作为 YAML mapping 解析 |
| `Clash.Meta` | 200 | YAML mapping | 包含 `proxies` 与 `proxy-groups` |
| `ClashForAndroid/2.5.12` | 200 | YAML mapping | 包含 `proxies` 与 `proxy-groups` |

因此 `CONFIG INVALID: The root value must be a YAML mapping` 的根因是内容协商，不是 YAML
解析器错误。订阅客户端必须使用明确的 Clash Meta 身份和 YAML `Accept`；若服务仍返回 URI
列表，应报告“订阅不是 Clash/Mihomo YAML”，不得把 Base64 文本送入 YAML 清洗器。

“Proxy ... uses a forbidden local or multicast server address”来自订阅中的额度/到期信息占位节点。
这类条目没有可路由价值，而且让 Mihomo 连接 loopback 会扩大本机服务访问面。导入事务应隔离
所有本地、未指定、链路本地和组播地址的静态节点，清理策略组对它们的引用，并在摘要中报告隔离
数量；只要仍有可用节点/provider/策略组，不能因为占位项而拒绝整份订阅。重复名称、非法 YAML、
越界 provider、无任何可用出站源等结构性错误仍终止导入。

未开启外部 VPN 且首次订阅主机本身不可达时，应用没有任何可用于联系订阅服务器的初始节点。
这是网络可达性的物理约束，软件不能凭空建立出口。首次导入提供两条明确路径：

- 临时开启系统 Clash/VPN，只完成第一次 URL 导入，之后关闭外部 Clash 并使用 Kiyori Mihomo；
- 从文件选择器导入已下载的 Clash/Mihomo YAML。

已有可用配置且内嵌核心运行时，“更新订阅”通过当前 Kiyori Mihomo 获取新配置；新响应完成解析、
清洗和 Mihomo `-t` 验证后才原子替换现有配置。任何更新失败都不改变已保存配置和当前核心。

## 唯一状态所有者

应用级 owner 位于 `com.kiyori.platform.network`，由以下组件协作但不复制状态：

| 组件 | 单一职责 |
| --- | --- |
| `KiyoriNetworkProxyConfigStore` | Android Keystore AES-256-GCM + `AtomicFile` 持久化 |
| `MihomoConfigSanitizer` | YAML 结构、安全边界、策略组清洗与运行配置重建 |
| `MihomoSubscriptionClient` | 订阅 URL 校验、内容协商、大小/重定向/UTF-8 检查 |
| `KiyoriMihomoRuntime` | 核心启动、私有端口、Controller、策略切换、进程退出与明文清理 |
| `KiyoriNetworkProxyLogStore` | 当前进程内有界脱敏日志、复制/导出文本和清空状态 |
| `KiyoriNetworkProxyManager` | 路由解析、系统 VPN 门禁、OkHttp/WebView/mpv 适配和状态投影 |

上一版 `ScriptNetworkConfigStore`、`ScriptProxyRuntime`、`ScriptNetworkHttpClientFactory` 与
`ScriptNetworkSettingsScreen` 不再作为兼容入口保留。Kiyori 尚无公开发行渠道，本轮直接建立
schema v4 的应用级配置；schema v2 仅把旧 `PROXY` 映射到全局语义并补齐规则字段，schema v3 旧自定义规则
会把 `*.`/`.` 前缀迁移为显式域名后缀类型，不读取或迁移本机
开发阶段的 `script_network` 私有目录。

## 持久化模型

```text
KiyoriNetworkProxyConfig(schemaVersion = 4)
├── enabled: Boolean = false
├── defaultMode: RULE | GLOBAL | DIRECT
├── moduleModes: Map<NetworkModule, INHERIT | DIRECT | PROXY>
├── scriptModes: Map<packageName, INHERIT | DIRECT | PROXY>
├── customRules: List<id / DOMAIN|DOMAIN-SUFFIX|DOMAIN-KEYWORD / pattern / DIRECT|PROXY / enabled>
├── subscriptions: List<KiyoriProxySubscription>
│   ├── id / displayName / sourceType
│   ├── url // URL source only
│   ├── sanitizedYaml
│   ├── rules // 保留的直接规则；更新时随订阅替换
│   ├── createdAtEpochMillis / updatedAtEpochMillis
│   ├── summary(nodes, groups, providers, isolatedEntries)
│   ├── usage(upload, download, total, expire) // 响应存在时
│   ├── selectedGroupItems: Map<groupName, itemName>
│   └── lastNodeTests(name, type, groupNames, delay, status, testedAt)
├── activeSubscriptionId: String?
├── proxyPrivateNetworks: Boolean = false
├── allowConcurrentSystemVpn: Boolean = false
└── testUrl: HTTPS URL
```

订阅 ID 在创建时生成并保持稳定；显示名可以编辑，URL path/query 不进入列表摘要。第一份成功导入的
订阅成为当前订阅，后续导入只加入订阅库，不改变当前链路。`activeSubscriptionId` 必须为空或指向
库内唯一条目。Kiyori 尚未公开发行，上一份无法在 Android Keystore 完成首次保存的 schema v1
不作为兼容 owner 保留。

`enabled=false` 是总开关：所有模块均在应用层直连，但不抹掉用户已选模块、当前订阅和策略。
`enabled=true` 时先取模块 override，再取 `defaultMode`；脚本请求在模块结果之上应用对应 package
override。模块/脚本的 `PROXY` 表示使用顶部当前模式；规则模式中的自定义域名规则永远排在订阅规则
之前。任何非 `DIRECT` 路由都要求当前订阅有效、根策略已选择、系统 VPN 门禁通过且核心处于
`RUNNING`。

### 订阅操作事务

| 操作 | 持久化与运行语义 |
| --- | --- |
| 添加 URL | 下载、清洗、`mihomo -t` 通过后创建新 ID；重复 URL 明确拒绝 |
| 导入 YAML | 清洗、`mihomo -t` 通过后创建本地条目；不继承其他条目的 URL |
| 刷新状态 | 只重读当前本地 Controller 的组、节点与运行状态，不下载订阅 |
| 更新订阅 | 使用该条目的 URL 下载并原子替换内容；保留仍有效的组选择 |
| 重新导入 | 用户重新选择 YAML 后原子替换指定本地条目 |
| 编辑 | 名称单独保存；URL 变化必须与新内容下载和校验在同一事务提交 |
| 复制 | 复制加密内容到新 ID 和新名称，不切换当前订阅 |
| 切换 | 保存明确选择的 ID 后协调主 runtime；失败不自动改回旧 ID |
| 删除 | 二次确认；当前订阅仍被代理路由使用时要求先切换或关闭总开关 |

配置写入完成而 runtime 应用失败时，UI 显示“设置已保存，代理运行未生效”和具体错误码；只有
Keystore/文件原子写入本身失败才显示“未保存”。所有持久化在 IO dispatcher 执行。AES-GCM 的
随机 IV 必须由 Android Keystore 在 `Cipher.ENCRYPT_MODE` 初始化时生成并从 `cipher.iv` 读取，
不能在 `setRandomizedEncryptionRequired(true)` 的密钥上提交调用方 IV。

### 模块枚举

| 模块 | 主要入口 | 说明 |
| --- | --- | --- |
| `AI_SERVICES` | 主模型、模型列表、云端 embedding、语音/STT/TTS | AI Provider 与流式/WebSocket 客户端 |
| `AI_TOOLS` | `http_request`、web visit、宿主下载等普通 AI 工具 | 不含已识别为传统脚本的请求 |
| `BROWSER` | Browser Runtime WebView、Browser 辅助请求、用户脚本远端资源 | WebView 代理覆盖当前进程全部 WebView，loopback 始终旁路 |
| `DOWNLOADS` | Browser 文件下载、模型/扩展资产下载 | 每个任务创建时冻结端点与局域网旁路策略；HEAD、Range、重试和并发分片不漂移 |
| `PLAYER` | mpv HTTP/HTTPS、HLS/DASH、字幕与封面 | HTTP 仍使用 mpv 代理；HTTPS 直链和 HLS 媒体通过仅监听 IPv4 loopback 的流式桥接转发到同一 `PLAYER` 路由，DASH 继续由 mpv 原生协议能力处理并待设备验收 |
| `SCRIPTS` | 传统 `JsEngine` 脚本包的标准宿主网络 | 支持逐 package override |
| `APP_SERVICES` | GitHub、市场、天气、规则订阅、Coil 网络图片 | Kiyori 自身在线能力 |

WebView 的 AndroidX `ProxyController` 是进程级 API，不能把不同 WebView 可靠拆成多个代理模式；
所有远程 WebView 因此归入 `BROWSER`。本地 `127.0.0.1`、`localhost` 与应用内页面必须旁路。
播放器由独立 mpv 网络栈持有，不能依赖 Java `ProxySelector`；由于 mpv `http-proxy` 不覆盖 HTTPS，
应用代理模式下的 HTTP(S) 直链和 HLS 媒体由 `PlayerMediaStreamBridge` 转成 IPv4 loopback HTTP 流，桥接端点使用
同一 `PLAYER` 路由并转发 Range/请求头/响应流。OkHttp 与 `HttpURLConnection`
分别使用 manager 提供的动态 selector/显式 `Proxy`，不设置 JVM 全局 system property。显式
`URLConnection` 也必须先执行相同的 loopback/私网旁路判断，不能因绕过 `ProxySelector` 把局域网
请求发送给 Mihomo。

## Mihomo 配置与策略组

### 输入边界

- 只接受单文档、严格 UTF-8、最大 4 MiB 的 YAML mapping。
- 禁止重复 key、递归 key、非标量 key、过量 alias 与超过 64 层的结构。
- 删除订阅提供的所有 inbound、TUN、LAN、sniffer、Controller、secret、profile 路径和 listener。
- 静态节点必须有唯一名称、类型和安全的 server；本地/私有字面地址条目被隔离并计数。
- provider 只允许 `http` 类型和绝对 HTTP(S) URL；provider 本地 path 改写到应用私有目录。
- DNS 只保留不依赖订阅规则集和外部地理数据库的出站解析配置；删除 `listen`、GeoSite/GeoIP
  过滤引用、`proxy-server-nameserver` 和 `respect-rules` 业务规则耦合。运行配置强制 `enable=true`、
  `ipv6=false`、`respect-rules=false`；订阅没有 DNS 段时使用受控的 IP nameserver。Kiyori 运行配置
  不能把已经移除的规则集依赖继续带入全新私有工作目录，也不允许订阅开启本机 DNS listener。

### 组清洗

1. 先收集合法节点、provider 与全部唯一组名。
2. 对每个组只保留 Mihomo v1.19.30 正式支持的出站选择字段；`proxies` 只引用合法节点、合法组或
   Mihomo 内建出站，`use` 只引用合法 provider。保留 `include-all`、`include-all-proxies`、
   `include-all-providers`、`filter`、`exclude-filter`、`exclude-type`、`timeout`、
   `empty-fallback` 等动态组字段；已从固定版本移除的 `relay` 组不进入运行配置。
3. 删除被隔离节点和无效组的引用，重复迭代直到引用图稳定；没有任何可选项目的组不进入运行配置。
4. 订阅组延迟测试 URL统一改为 Kiyori 的 HTTPS 测试 URL，防止订阅借测试功能访问本地服务。
5. 新建保留名 `KIYORI_APP_PROXY` 的根 `select` 组，候选项为清洗后的订阅组和静态节点，provider
   只在没有静态候选时直接挂载；唯一规则为 `MATCH,KIYORI_APP_PROXY`。
6. Controller 读取 `/proxies` 和 `/group` 构造组与节点快照；用户选择通过带随机 bearer secret
   的 loopback Controller 写入。根组和所有 `select` 组的选择按订阅独立持久化，并在该订阅成为
   当前运行配置后逐一应用。
7. 单节点测速调用 `GET /proxies/{name}/delay`；整组测速调用 `GET /group/{name}/delay`，后者按
   Mihomo 官方实现并发返回 `节点名 -> delay`，不能继续把组名发送到单代理端点后伪装成组结果。

UI 展示真实订阅组名、组类型、当前项目、节点数量与延迟状态。`select` 组可手动选择；自动策略组
只展示其当前项目并允许触发延迟测试，不伪装成手动选择。保留组使用用户文案“默认代理”，不在
界面暴露 `KIYORI_APP_PROXY` 技术名。

### 非当前订阅节点探测

主 runtime 只加载当前订阅。查看或测速其他订阅时，`KiyoriMihomoRuntime` 在独立私有 probe 目录
启动短生命周期 Mihomo：使用独立 mixed-port、Controller port 和 secret，读取组/节点并执行
指定单节点或整组测速，完成后停止进程并删除明文。probe 不修改主 runtime、当前订阅或模块路由，
同一进程最多运行一个 probe；UI 显示订阅 ID 对应的进度并禁用重复操作。探测结果写回该订阅的
加密记录，provider 动态节点因而可以在不切换当前代理的情况下显示。探测失败只更新可见错误，
不得自动切换订阅、节点或网络路线。

## 内嵌核心与安全

- 继续固定 `MetaCubeX/mihomo v1.19.30`、commit、压缩包 SHA 与 ELF SHA；本轮不升级依赖。
- APK 只打包官方 arm64 PIE 核心和 parent-death launcher；核心退出、应用进程死亡或 launcher
  父进程死亡都必须结束运行时。
- 每次启动分配随机 loopback mixed-port、Controller port、Controller secret。Controller 仅供
  Kiyori 使用；订阅中的 Controller 配置永远不生效。
- Browser 无法可靠提交 proxy basic auth，因此 mixed-port 不使用静态凭据；随机高位端口、
  loopback bind、短生命周期和应用退出联动共同缩小暴露面。Controller 仍强制随机 bearer secret。
- 运行 YAML 写入 no-backup 私有目录，权限收紧；Mihomo 完成读取并通过 Controller readiness 后
  删除明文。启动前先清理上次崩溃留下的运行目录。
- 日志只在当前应用进程内保留最近的有界事件，覆盖订阅校验、核心启动/停止、选点、测速和错误。
  Mihomo 输出进入日志前必须裁剪并遮蔽 URL user-info/query、Bearer、secret/password/token、UUID、
  私有文件路径和长凭据；不得记录订阅 YAML、代理认证、Controller secret 或响应正文。查看之外的
  复制、SAF 导出和清空均由用户显式操作，导出不申请额外存储权限。

## 外部 Clash / 系统 VPN 共存

Kiyori 内嵌 Mihomo 是应用层 HTTP 代理，不占用 Android `VpnService`，因此可以检测到外部 Clash
VPN。确定行为如下：

| Kiyori 内嵌开关 | 系统 VPN | 模块模式 | 实际链路 |
| --- | --- | --- | --- |
| 关闭 | 关闭 | 任意 | 模块 -> 网络 |
| 关闭 | 开启 | 任意 | 模块 -> 系统 VPN |
| 开启 | 关闭 | 直连 | 模块 -> 网络 |
| 开启 | 关闭 | 代理 | 模块 -> Kiyori Mihomo -> 节点 |
| 开启 | 开启，未授权并存 | 代理 | 拒绝启动/停止核心并显示 `VPN_CONFLICT` |
| 开启 | 开启，已授权并存 | 代理 | 模块 -> Kiyori Mihomo -> 系统 VPN -> 节点 |
| 开启 | 开启 | 直连 | 模块 -> 系统 VPN |

因此界面不再显示“外部 HTTP / Clash mixed-port”的主机、端口、用户名、密码。外部 Clash 使用
VPN 模式时无需向 Kiyori 填任何值；若外部软件只开放 mixed-port 而没有 VPN，Kiyori 本阶段不为
它建立第二个手动代理 owner。

## 设置页面设计

入口固定为“设置首页 -> 更多功能 -> 网络代理”。`KiyoriSettingsRoute.NETWORK_PROXY` 压入当前
settings route stack，Back 恢复“更多功能”，再 Back 恢复设置首页和原 Browser/AI 来源。

页面沿用 `KiyoriCollapsingSettingsPage`、冷灰页面、白/深灰分组卡、双行 row、`0.6dp` divider、
青色网络语义图标和 `26dp` 底部选择面板，不创建卡片套卡片。主页只保留三组内容，结构从上到下固定为：

1. **应用内代理**：`启用应用内代理` 开关和 `默认连接`（直连/代理 segmented control）。总开关关闭时不改变用户
   已保存的模块/节点选择，只让请求按直连处理。
2. **连接范围**：四个导航行分别进入“节点选择”“订阅管理”“模块连接模式”“逐脚本连接模式”，不再在主页重复显示
   当前路由、当前订阅或策略组摘要。
3. **网络选项**：`代理日志` 子页面、`代理局域网地址`、`允许与系统 VPN 并存` 两个开关和
   `重置网络代理`。主页不再展示测试地址或外部 HTTP/mixed-port 主机、端口、认证表单。

“代理日志”子页实时显示当前进程最近事件；标题栏从左到右提供复制、导出两个图标按钮，页面底部
提供带确认的清空操作。空日志、导出取消、目标文件写入失败和日志并发更新都必须有确定反馈。

“节点选择”子页只使用当前订阅：顶部标题行右侧固定为“测速、排序”，测速直接测试当前横向标签对应的整组，排序提供
默认/名称/延迟。标签下方是只过滤当前组的搜索框；节点单列逐行显示，左侧无图标，标题最多三行且不截断，右侧独立
测速按钮。点击 `select` 组节点主体直接保存选择，自动策略组主体不可选；嵌套策略组显示为“策略组”项目，不伪装成节点。

“订阅管理”子页顶部提供“添加订阅地址”和“导入 YAML 文件”两个按钮。订阅每项一行，点击行立即切换当前订阅；右侧
三点菜单只有“更新、编辑、复制、删除”。URL 更新、本地 YAML 重新导入、复制副本和删除确认均继续由管理器完成下载/清洗/
核心校验后的原子保存。模块和逐脚本规则各自独立成页，保留真实模块、脚本发现、刷新脚本和手动包名规则。

订阅编辑弹窗包含名称和 URL，不显示 external host/port/auth。URL 输入按敏感字段处理，默认
隐藏 path/query，用户点击眼睛图标后才展示完整值。下载、清洗、核心校验和保存期间弹窗保持打开，
确认按钮显示固定尺寸进度，失败原因就地显示；成功后才关闭。文件导入使用 Android `OpenDocument`，
只读取用户明确选择的文件，并在订阅卡内显示读取、校验、保存结果。所有冲突动作在任一事务执行时
禁用，避免重复点击覆盖配置。

### 脚本环境变量入口

“左抽屉 -> 扩展 -> 脚本 -> 顶栏第一个设置按钮”直接打开现有环境变量下拉抽屉，不再出现
“脚本设置 / 环境变量 / 网络代理”选择弹窗。抽屉底部动作行调整为：

```text
[网络代理]                         [取消] [保存]
```

“网络代理”使用 network 图标 + 文本，位于同一行左侧。点击时先关闭抽屉，不保存草稿，然后通过
Kiyori App Shell 的设置 route callback 以 `AI_HOST` 来源打开
`HOME -> MORE_FEATURES -> NETWORK_PROXY`。Shell 继续是 route stack 的唯一状态 owner。

## 失败语义

| 错误码 | 用户含义 | 状态动作 |
| --- | --- | --- |
| `CONFIG_MISSING` | 尚未导入订阅或没有默认策略 | 不启动核心 |
| `CONFIG_INVALID` | YAML/schema/引用图/Mihomo `-t` 不通过 | 导入事务不提交 |
| `SETTINGS_WRITE_FAILED` | Keystore 或原子文件写入失败 | 明确报告未保存 |
| `SUBSCRIPTION_DUPLICATE` | URL 已存在于订阅库 | 指向已有条目，不创建副本 |
| `SUBSCRIPTION_FORMAT` | 响应不是 Clash/Mihomo YAML mapping | 提示检查订阅类型 |
| `SUBSCRIPTION_FAILED` | DNS、连接、TLS、HTTP、大小或重定向失败 | 保留当前配置 |
| `VPN_CONFLICT` | 外部 VPN 存在但未授权并存 | 停止内嵌核心 |
| `CORE_MISSING` | APK 未包含固定核心/launcher | 禁止开启 |
| `CORE_START_FAILED` | 验证、启动、Controller 或进程异常 | 状态进入 ERROR |
| `GROUP_SELECTION_INVALID` | 保存的组项目不再存在 | 要求用户重新选择 |
| `PROXY_CONNECT_FAILED` | 请求无法通过当前内嵌核心 | 当前请求失败 |

页面显示本地化、可执行的短消息；代理日志保留阶段、错误码和经过脱敏的 Mihomo 校验原因。不得把
原始核心输出直接作为主 UI，也不得在失败后自动修改总开关、模块模式、节点或测试 URL。

## 实施顺序

1. [DONE] 复核现有脚本实现、设置 route、主要网络栈、固定 Mihomo 制品和目标订阅响应结构。
2. [DONE] 根据真机反馈定位 Android Keystore 调用方 IV 根因，并对照 Mihomo v1.19.30 commit
   `ac017cdd246ce8bd547653d927e7bf77d7ee73d5` 核对组字段与 Controller 测速 API。
3. [DONE] 将单订阅 schema 重构为多订阅库，修复 Keystore/IO/错误事务并实现订阅刷新、添加、
   切换、更新、编辑、复制和删除。
4. [DONE] 扩展 sanitizer 的动态组字段，增加主 runtime 节点发现和独立 probe runtime，完成
   单节点/整组测速及按订阅持久化结果。
5. [DONE] 重做 Compose 页面中的订阅库、动作抽屉、上下文进度、分组节点和错误反馈。
6. [DONE] 将脚本专属模型、store、sanitizer、subscription client 和 runtime 重构为
   `com.kiyori.platform.network` 唯一 owner，并删除旧界面与旧私有配置路径。
7. [DONE] 接入 OkHttp、`HttpURLConnection`、WebView ProxyController 和 mpv；为每个模块补充
   定向合同测试，保证普通 AI 工具与传统脚本身份不会串线。
8. [DONE] 更新 `CONTEXT.md`、中英文 README、合同测试和 TODO 最终状态。
9. [DONE] 运行定向 JVM/Python、formal readiness、architecture、Markdown/translation、
   `git diff --check` 和规定 Debug APK 构建；核验 APK 内 Mihomo/launcher 与签名/对齐。
10. [DONE] 审计精确候选树、敏感内容、私有订阅、缓存、大文件、子模块和远端竞争，提交并推送
    `main`，核对 local/tracking/remote ref。
11. [PENDING] 在目标设备完成真实订阅、多订阅切换/测速、外部 VPN 并存、Browser、AI、播放器、
   下载器、脚本和进程
   生命周期验收。
12. [DONE] 修复设置首页在浏览器往返后丢失 settings session 导致的 `Settings route requires an active settings session` 崩溃；当前路由和传统脚本发现加入 JVM 回归覆盖。
13. [DONE LOCALLY] 将模块连接和策略组/节点从主页面平铺列表收敛为两个页面内二级视图，并为策略组增加横向标签切换与“选择 / 测速”分离的交互。
14. [DONE LOCALLY] 按用户确认的最终布局收敛为节点选择、订阅管理、模块连接模式、逐脚本连接模式四个子页；移除
    当前路由/当前项目/刷新节点与分组/布局菜单/测试地址等冗余入口，订阅行切换与右侧四项溢出菜单完成。
15. [DONE LOCALLY] 使用目标订阅的私有临时副本和固定 Mihomo v1.19.30 证明旧配置会在空目录下载
    GeoSite/GeoIP 数据；修复 DNS 清洗后同版本核心校验为退出码 0 且不生成外部数据文件。私有订阅和
    生成配置未进入 Git 或任务记录，并已从系统临时目录删除。
16. [DONE LOCALLY] 增加单一进程内脱敏日志 owner，接入配置校验、主/probe runtime、选点和测速链路；
    意外核心退出无论退出码是否为零都必须保留可诊断原因，同时继续清理明文运行配置。
17. [DONE LOCALLY] 在网络代理主页增加“代理日志”入口，完成实时查看、复制、SAF 导出和确认清空子页面。
18. [DONE LOCALLY] 补充 DNS 地理数据依赖剥离、日志脱敏/有界性、UI 合同与 Mihomo 错误输出回归，完成
    定向/完整 JVM、Python、正式开发门禁和规定 Debug APK 构建；真机复测继续保持 `verification_pending`。

## 自动验证矩阵

- 模型策略：总开关、默认模式、逐模块、逐脚本、VPN 门禁、局域网地址策略。
- YAML：目标服务同类 Clash YAML、Base64 格式拒绝、占位节点隔离、策略组引用清理、重复名、
  provider path、单文档、UTF-8、4 MiB、alias、深度、DNS GeoSite/GeoIP 依赖剥离和 secret 不出现在异常。
- Runtime：随机 loopback port、无 LAN/TUN、Controller bearer、组快照/切换、旧明文清理、核心
  异常退出（含退出码 0）、parent-death、配置指纹变化、校验错误输出收集和停机幂等。
- 网络入口：AI 主模型与模型列表、普通 AI 工具、传统脚本四入口、Browser WebView、Browser
  下载、AI/市场/MCP/Skill/语音/Compose DSL/Markdown 图片、mpv option、Kiyori 服务 client
  均使用明确 module；本地 URL 始终旁路；多段下载任务内路由固定。
- UI/导航：More Features 顺序、NETWORK_PROXY Back 链、环境变量按钮直接开抽屉、底部左按钮、
  主页新增代理日志入口、日志查看/复制/SAF 导出/确认清空、节点选择横向分组/搜索/整组测速/排序/单列节点、
  订阅行切换与更新/编辑/复制/删除菜单、模块/脚本子页、订阅空/有数据/加载/错误、浅深主题、横屏和窗口尺寸。
- 仓库/APK：现有 Mihomo 固定 SHA、Gradle 下载/ELF/16 KB 检查、无订阅泄漏、无运行明文或大
  ELF 提交、Debug APK identity/signer/zipalign/native basename。

本轮本地证据（2026-08-23）：

- 目标订阅旧运行配置在空目录执行固定 Mihomo v1.19.30 `-t` 时生成 `GeoSite.dat` 与
  `geoip.metadb`；修复后的同版本校验退出码为 0，空目录生成文件数为 0。
- `MihomoConfigSanitizerTest`、`KiyoriNetworkProxyLogStoreTest` 与
  `KiyoriNetworkProxyPolicyTest` 定向通过；完整 App JVM 为 `295 suites / 1731 tests`，零失败、
  零错误、零跳过。
- `ci/test` 为 `228/228`，网络代理专项合同 `8/8`，formal readiness 与 `git diff --check` 通过；
  仓库扫描未发现目标订阅 host/token 特征。
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`，`235` tasks 中 `26`
  executed、`209` up-to-date；唯一 Launcher、Mihomo/launcher 和播放器运行时打包门禁通过。
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`493116749` bytes，SHA-256
  `8094AC5DB7A45D2C53B6AABCA7E2EECD899922A402BD46ADA1BC89F402846502`；包名/版本为
  `com.kiyori / 45 / 0.1.0`，Debug V2 单 signer 与 16 KB zipalign 通过。

## 2026-08-23 深度优化实施方案

### 问题证据与范围

本轮基线为 `main@fa61b9b5`，工作树干净，正式开发准备门禁已通过。现有实现已经具备订阅库、
策略组、单节点/整组测速和排序入口，但仍有以下闭环问题：

1. 用户可见的主页入口、子页标题、分组描述和空状态仍使用“当前节点”；截图中的长节点名进入设置行
   的固定 `value` 区域后被省略，用户无法确认完整选项。
2. 节点排序虽然已有菜单，但排序状态只属于当前 Compose 会话，默认顺序、同延迟稳定顺序和未测速
   节点位置没有明确契约；切组、切订阅和返回页面时也缺少统一的状态重置规则。
3. `KiyoriNetworkProxyManager` 在 `mutationMutex` 内执行完整 probe 启停、Controller 请求、快照
   合并和加密写入；慢节点或整组超时会阻塞其他设置操作。测速结果仍必须由 Mihomo 官方 group delay
   接口产生，不能改为客户端伪并发或吞掉失败。
4. 组测速/单节点测速的 UI 反馈没有区分“当前组整组测速”和“单项测速”，长名称会进入操作提示和
   日志，增加布局压力和可读性风险。

### 实施决策

- Kiyori 当前仍是未公开产品（现有正式准备文档与本专项状态均如此记录），因此本轮直接统一用户可见
  文案为“节点选择”，不保留“当前节点”兼容显示；内部 `NetworkProxyPageSection.CURRENT_NODE`
  和网络协议标识不改，避免扩大持久化/路由迁移范围。
- 主页节点摘要改为两行布局：固定标签“节点选择”与可换行的节点/策略链摘要分离，节点名称最多三行
  且不通过 `KiyoriSettingsRow.value` 承载；节点页继续单列逐行显示，测试按钮与选择点击区域分离。
- 排序定义为稳定投影：默认严格保留 Mihomo/订阅返回顺序；名称按 `Locale.ROOT` 的不区分大小写名称
  升序并以原顺序稳定打破相同名称；延迟按成功延迟升序，未测速/失败/超时统一置底，再按名称和原顺序
  稳定打破。切换分组或订阅时保留用户选择的排序模式，离开节点页时只关闭菜单和清空搜索。
- 测速仍由 `KiyoriMihomoRuntime` 的单次 Controller `/group/{name}/delay` 或
  `/proxies/{name}/delay` 完成；管理器只在需要保护同一订阅的快照合并与持久化时互斥，probe 网络等待
  不占用全局设置写入锁。相同订阅的测速请求在 UI 层仍禁止重复提交，其他订阅/设置操作不会被无关等待
  阻塞；任何异常都记录到脱敏日志并显式反馈。
- 不新增第二状态 owner、不改变 schema、Mihomo 版本、测试 URL、路由策略或 VPN/TUN/LAN 边界；不加入
  fallback、静默重试、伪造延迟或自动切换节点。

### 实际修改文件与验证

实际修改：

- `app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriNetworkProxySettingsPage.kt`：文案、
  主页摘要、长名称约束、稳定排序和测速反馈。
- `app/src/main/java/com/kiyori/platform/network/KiyoriNetworkProxyManager.kt`：测速任务的锁边界与
  结果提交一致性。
- `app/src/test/java/com/ai/assistance/operit/ui/main/shell/KiyoriNetworkProxySettingsPolicyTest.kt` 与
  `app/src/test/java/com/kiyori/platform/network/KiyoriNetworkProxyPolicyTest.kt`：排序规则、状态重置和
  网络路由策略边界。
- `CONTEXT.md`、本文件：同步用户可见术语、状态所有权与验收契约。

验证顺序：`git diff --check`；网络代理定向 JVM 测试；必要的 App JVM 回归；
`python -B ci/script/check_formal_readiness.py --repository . --require-main`；
`./gradlew :app:assembleDebug --no-daemon --console=plain` 并核验 `app/build/outputs/apk/debug/app-debug.apk`；
最后审阅精确 diff、敏感内容、子模块/产物和 `main` 远端 ref，再提交推送。真机 UI、真实节点连通性、
系统 VPN 并存和进程生命周期继续单独保持 `verification_pending`。

### 2026-08-23 节点页标题栏真机反馈修复

真机截图确认节点入口已经显示“节点选择”，但子页大标题仍显示“当前节点”，且标题栏只显示测速按钮。
源码核对确认两个问题互相独立：子页标题仍取 `NetworkProxyPageSection.CURRENT_NODE` 的旧显示值；
排序按钮已经接入，但共享折叠标题栏把整个 `headerAction` 固定限制在单按钮 `48dp` 宽度，两个按钮中的
第二个被父容器裁剪。

本轮保留内部 `CURRENT_NODE` 路由标识，只把用户可见标题绑定到唯一“节点选择”术语；共享标题栏增加
显式操作区宽度合同，默认单按钮页面继续使用 `48dp`，网络代理页使用 `96dp` 并同步扩大标题右侧留白，
使测速和排序各自拥有稳定的 `48dp` 点击区域。排序菜单和既有“默认、名称、延迟”逻辑不建立第二状态
owner，也不改变节点测速、路由或持久化行为。

定向 `KiyoriSettingsPagesTest` 与 `KiyoriNetworkProxySettingsPolicyTest` 合计 `23/23` 通过，
`compileDebugKotlin`、正式开发准备检查和 `git diff --check` 均通过；标题栏的无操作、单按钮、双按钮
右侧留白分别固定为 `16dp`、`64dp`、`112dp`。最终用户可见布局仍需使用本轮 Debug APK 在目标设备复测。

## 真机验收

1. 外部 Clash 关闭：从本地 YAML 首次导入，开启 Kiyori 代理，Google、DuckDuckGo、Brave 与
   非 Search 脚本可用。
2. 外部 Clash 临时开启：使用 URL 首次导入目标订阅，确认策略组、节点、流量摘要和占位隔离数；
   关闭外部 Clash 后由 Kiyori 独立联网并更新订阅。
3. Browser 打开被阻断网站并测试登录、Cookie、WebSocket、下载、无痕和本地页面；直连模式与
   代理模式按模块切换。
4. AI 主模型完成普通与流式响应，语音/embedding 按 AI 模块路由；AI 工具和脚本按各自模块路由。
5. mpv 播放 HTTP/HTTPS、HLS/DASH 与字幕，切换视频、前后台和服务重建后仍使用快照模式；HTTPS 直链与 HLS 的 bridge 语义需在设备验收，DASH 不宣称已由 bridge 重写。
6. 系统 VPN 存在且未授权时内嵌核心明确拒绝；授权后验证双层链路；直连模块仍经过系统 VPN。
7. 旋转、分屏、前后台、进程强杀、订阅更新失败、核心异常退出、网络切换和重启后状态一致。

自动检查和 Debug APK 不能代替这些设备证据；设备完成前状态保持 `verification_pending`。

## 2026-08-23 应用级路由闭环修订

### 证据边界

用户提供的三份播放器诊断报告只保留以下最小事实：Android 网络快照与 `PlayerNetwork` 快照均为
`proxy=absent`；mpv 记录过 TLS handshake 被对端中止以及媒体加载失败；请求属于远程 HTTPS 视频
资源。报告没有记录 Kiyori 应用级路由解析结果，因此不能把系统代理缺失等同于“应用级代理一定未配置”，
也不把 TLS 对端中止单独归因于代理。完整 URL、query、Cookie、节点和订阅正文不进入仓库文档。

### 实施结果

- `KiyoriNetworkProxyConfigStore.currentConfig()` 在每次跨进程读取时比较存在性、长度、修改时间和
  加密头/随机 IV 前缀；`:player` 进程因此会重新读取主进程刚保存的配置，而不会沿用进程启动时缓存。
- `KiyoriNetworkProxyManager` 以一份 `ResolvedNetworkRoute` 同时保存配置、模块路由、Mihomo 端点和
  私网策略。任务级 `connectionFactoryBlocking()` 与 Browser `proxySelectorBlocking()` 在任务创建时
  冻结这份快照，避免 HEAD、Range、重试或并发分片在订阅/节点变化时分裂到不同端点。
- 显式 `HttpURLConnection` 路径与 `ScopedKiyoriProxySelector` 共用 loopback、localhost、任播/组播、
  link-local 和私网判断；`proxyPrivateNetworks=false` 时私网直连，设为 true 时公网与私网均按
  应用代理发送，loopback 仍保持直连。
- `HttpMultiPartDownloader` 不再内部创建裸连接；HEAD 探测、单段下载和全部 Range 分片都由调用方
  注入的工厂创建。AI 宿主下载因此进入 `AI_TOOLS`，Browser 下载进入 `DOWNLOADS`。
- 播放器独立 `:player` 进程在 mpv 初始化前和每次 `load()` 前重新解析 `PLAYER` 路由。初始化使用
  `http-proxy` option，播放会话中发生路由变化时更新 property，并读回确认实际属性值；诊断只输出
  `route=PROXY/DIRECT` 与脱敏端点，不输出 URL、Cookie、节点名或认证信息。
- 其他发现的裸 `HttpURLConnection` 生产入口已归类到 `APP_SERVICES`、`DOWNLOADS`、`AI_SERVICES` 或
  `BROWSER`。`GithubReleaseUtil` 的镜像测速函数仍无生产调用，本轮不为未使用工具建立第二套路由或
  扩大公开 API 范围。

### 自动验证与剩余验收

本轮通过 `:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin`，以及代理/下载/播放器定向
测试 `159 actionable tasks` 对应的 `testDebugUnitTest BUILD SUCCESSFUL`；正式开发准备、fresh clone、
architecture `phase=m03` 和 `git diff --check` 通过。规定的 `:app:assembleDebug --no-daemon
--console=plain` 为 `BUILD SUCCESSFUL in 51s`，`235` 个任务中 `23` 个 executed、`212` 个 up-to-date；
唯一 launcher、脚本代理运行时、播放器运行时和 native packaging Gradle 门禁通过。

APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `493116749` bytes，SHA-256 为
`F58C50D01808D6B677D6263CB8CC72EFF3F046AF0D644376D836695C97BE3748`；包身份为
`com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，仅含 `arm64-v8a`，唯一 launcher
为 `com.ai.assistance.operit.ui.main.MainActivity`。Android Debug V2 单 signer、证书指纹
`E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902` 和
`zipalign -c -P 16 -v 4` 均通过。

未安装 APK、未操作设备、未连接真实节点，状态保持 `verification_pending`。下一步最有价值的是用
同一订阅执行 Browser WebView、播放器、AI 多段下载和 Browser 下载的 `DIRECT/PROXY/私网` 矩阵，
再决定是否需要增加用户可见的按模块连接测试或代理健康检查入口；当前不新增设置选项。

## 2026-08-23 Mihomo runtime 生命周期修订

### 已确认问题

- `KiyoriApplication.onCreate` 会启动一次应用级协调，而设置写入路径也会协调同一 runtime。此前无参
  协调的默认配置在进入函数前读取，未与 `mutationMutex` 串行；旧配置可能在新配置已经启动 Mihomo
  后执行 `runtime.stop()`，造成短时 `ERR_PROXY_CONNECTION_FAILED`。现在协调函数在锁内读取当前加密配置，
  设置写入、启动协调和运行时刷新共用同一串行边界。
- `monitorProcess` 此前把所有退出码统一记录为“异常退出”。现在 `ActiveRuntime.expectedStop` 由
  `stopLocked` 在销毁进程前设置；按请求退出归档为 `STOPPED`/INFO，未被停止所有权标记的退出即使为
  `0` 仍归档为 `ERROR`/ERROR。旧 monitor 只有在观察到的 `Process` 仍是当前 runtime 时才可更新状态。

### 证据与边界

- 这次修订解释并消除了一个可复现的配置协调停止竞态，也修正了退出码 `0` 的诊断语义；它没有自动
  重启、自动换节点、静默直连或吞掉代理错误。
- 主进程日志在 `21:50:40` 的 `Mihomo shutting down` 仍需目标设备用新版日志确认触发者；本地构建无法
  证明真实订阅、Android 进程调度、上游节点稳定性或 MPV Range 连接是否已经恢复。
- `:player` 拥有独立的 `KiyoriMihomoRuntime` 和 runtime 目录；播放器报告中的 `route=PROXY` 只能证明
  mpv 获得了应用级代理，不能把主进程的 runtime 日志当成 `:player` 的 runtime 生命迹象。

### 本轮验证

- `:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin`、定向代理/下载/播放器测试和完整
  `:app:testDebugUnitTest` 均通过；`159 actionable tasks` 的测试任务零失败。
- `python -B ci/script/check_formal_readiness.py --repository . --require-main`、
  `python -B ci/script/check_fresh_clone.py --repository .`、
  `python -B ci/script/check_architecture_boundaries.py --repository . --require-main --phase auto`、
  `git diff --check` 均通过。Markdown 链接脚本当前只接受两个 Git tree/commit 参数；本轮文档没有
  移动文件或新增本地链接目标，dirty worktree 未伪造临时候选提交。
- `./gradlew :app:assembleDebug --no-daemon --console=plain` 通过，`235 actionable tasks`，
  Mihomo parent-death、脚本代理 runtime、播放器 runtime packaging 门禁通过。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `493116749` bytes，SHA-256 为
  `B492238288A650B2490BDA2EF2AC78A5CBF89CC6ECEE7F692121CD66B59F629A`。独立核验确认
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`、唯一 launcher、仅 `arm64-v8a`、
  Android Debug V2 单 signer 和 `zipalign -c -P 16 -v 4` 均通过。

## 2026-08-23 代理运行时诊断链路增强

### 新现场证据

- 两份播放器报告都记录了 `route=PROXY` 和同一形式的 loopback `mpvHttpProxy`，因此当前问题不能
  继续用“播放器没有应用代理”概括；报告仍缺少该端口是否可连接、Controller 是否健康、`:player`
  进程内 Mihomo runtime 的 generation/退出时间和停止触发源。
- 主进程代理日志只把核心输出与少数管理操作写入 1000 条进程内历史，没有宿主进程身份、runtime
  generation、端口、启动时刻、Controller/混合端口健康结果或 stop reason；因此同一份日志无法证明
  主进程和 `:player` 的事件是否属于同一个 runtime。
- Mihomo 是当前内嵌的 Clash.Meta 核心。外置 Clash Meta 的 TUN、系统全局代理、LAN listener 和
  客户端 UI 不是这次应用级 `ERR_PROXY_CONNECTION_FAILED` 的直接缺口；本轮不把这些能力移植到
  Kiyori，也不扩大既定的 loopback-only/application-only 边界。

### 本轮实施决策

1. 继续复用 `KiyoriNetworkProxyLogStore`、`KiyoriMihomoRuntime`、`KiyoriNetworkProxyManager` 和
   现有 `PlayerDebugLogBuffer`，不建立第二份代理状态或第二个日志数据库。
2. 每个进程的导出日志增加 process name/PID；每次 Mihomo 启动分配单调 runtime generation，并
   记录 mixed/controller 端口、启动时刻、配置校验、Controller readiness、混合端口监听检查、
   复用健康检查、选点/测速请求结果和 stop reason。核心原始行继续经过同一脱敏边界。
3. route resolution 与 WebView proxy override 记录 module、mode、VPN gate、runtime generation、
   endpoint port 和耗时；失败保留真实错误码与阶段，不自动切换节点、自动直连或吞掉错误。
4. `:player` 进程监听同一进程内的代理 runtime state，把 generation、端口、健康/退出状态和退出
   原因写入既有播放器诊断报告，使“mpv 代理选项已设置”和“Mihomo 端口实际可用”可以分开判断。

### 验收条件

- 单元测试覆盖 runtime generation/stop reason、主动停止与自然退出、Controller/混合端口健康结果、
  进程上下文脱敏导出和 route/播放器诊断投影。
- Debug APK 构建通过，且现有代理、下载器、播放器和完整 JVM 回归不退化。
- 真实设备必须用同一订阅分别导出主进程代理日志与播放器报告，按 process/PID、generation、端口和
  时间对齐 `Mihomo shutting down`、TLS/Range 错误以及 Controller/混合端口健康结果；在此之前本专项
  状态继续为 `verification_pending`。

本轮不新增设置选项；是否需要按模块健康检查按钮、连接诊断向导或更多 Clash.Meta 功能，待上述现场
证据闭合后再决定，避免用新 UI 掩盖尚未归因的 runtime 或上游节点故障。

### 本轮实现与本地验证

- `KiyoriNetworkProxyLogStore` 的导出头现在包含进程名/PID；`KiyoriMihomoRuntimeState` 和核心日志
  包含 runtime generation、mixed/controller 端口、Controller/混合端口健康、启动时刻和 stop reason。
- runtime 复用前执行 Controller 与 loopback mixed-port 健康检查；失败进入明确 `ERROR` 并阻断当前
  代理路由；运行中连续两次健康失败会停止旧 generation，进入同一受限自动恢复协调，不创建第二核心
  或静默改路由。所有 manager route resolution 与 VPN 冲突 stop 现在位于同一 `mutationMutex` 边界；
  复用健康检查使用 `800ms` 独立 Controller 超时，健康看护每 `10s` 检查一次，避免核心卡死时拖住
  网页/播放器 route setup。
- `MpvPlayerEngine` 在代理解析失败或成功时写入 runtime health 投影；`:player` 的
  `PlayerRuntimeService` 监听本进程 proxy runtime state，使用共享诊断格式写入现有播放器报告。
- 变更涉及 `KiyoriMihomoRuntime.kt`、`KiyoriNetworkProxyManager.kt`、`KiyoriNetworkProxyLogStore.kt`、
  `BrowserWebViewSupport.kt`、`BrowserDownloadTransport.kt`、`HttpMultiPartDownloader.kt`、
  `MpvPlayerEngine.kt`、`PlayerRuntimeService.kt`、对应代理/下载测试和 `CONTEXT.md`；未改变订阅
  schema、Mihomo 版本、端口暴露边界、模块模式或 UI 设置项。
- 定向编译与代理/播放器专项测试通过；完整 `:app:testDebugUnitTest` 为 `299` 个 XML 报告、`1747`
  个测试，`0 failures / 0 errors / 0 skipped`。formal readiness、fresh clone、architecture
  `phase=m03` 和 `git diff --check` 通过。
- `:app:assembleDebug --no-daemon --console=plain` 通过，`235` actionable tasks；Mihomo
  parent-death、脚本代理 runtime、播放器 runtime packaging 和唯一 Debug launcher 门禁通过。
  APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `493116749` bytes，SHA-256 为
  `74B9EEE50D11B37B6F2AB60B128429BE972C54DC62E03C93936C136E3A429F2C`；包身份为
  `com.kiyori / 45 / 0.1.0`，仅 `arm64-v8a`，Android Debug V2 单 signer 和
  `zipalign -c -P 16 -v 4` 均通过。

## 2026-08-23 异常自然退出自动恢复

### 现场根因边界

- 新日志确认主进程 `runtimeGeneration=1` 在 `15:26:45` 记录 `Mihomo shutting down`，随后以退出码 `0`
  结束；直到 `15:28:12` 下一次协调才出现 generation 2。这段时间 mixed-port 已不存在，能够解释整站
  `net::ERR_PROXY_CONNECTION_FAILED`，属于代理 runtime 空窗，不是播放器未设置 `http-proxy`。
- 同一批历史日志另有 `context deadline exceeded`、上游节点拨号 `i/o timeout`、mpv TLS 被对端中止和
  Range 媒体 `partial file`。这些属于上游节点、目标站点或媒体服务连接质量问题；核心进程重启不能将
  它们伪装成已修复，也不触发本次异常进程恢复。
- 现场导出没有出现对应的 `正在停止内嵌 Mihomo`，且导出头中的宿主 PID 在前后保持一致，因此当前能
  确认的是“未被现有 runtime 停止所有权记录的自然退出”，发送停止信号的更细来源仍需设备级日志和
  系统进程证据继续确认。

### 自动恢复契约

- `KiyoriMihomoRuntime` 为自然退出发布 `failureKind=UNEXPECTED_PROCESS_EXIT`；运行中连续两次本地
  Controller/mixed-port 健康检查失败发布 `failureKind=HEALTH_CHECK_FAILED`。按请求停止和启动失败分别
  保持独立状态，不进入自动恢复触发条件。
- `KiyoriNetworkProxyManager` 监听同一进程的 runtime state，在 `mutationMutex` 内重新读取当前加密配置、
  VPN 状态、订阅和模块路由后执行一次协调。用户关闭代理、切换订阅或发生 VPN 冲突时，当前配置优先，
  不会用旧订阅重新拉起核心。
- 每个失败 generation 只触发一次；同一进程五分钟内最多自动恢复两次，超过限额保持 `ERROR` 并记录
  限额原因，避免核心持续退出或健康失效时形成重启循环。恢复失败保留错误状态，不自动换节点、静默
  直连或吞掉原始故障。
- Browser WebView 在恢复协调中重新安装新 mixed-port；`:player` 在新 generation 进入 `RUNNING` 后
  在既有 mpv 线程刷新 `http-proxy`。已经创建的下载任务继续使用其任务级路由快照，恢复后新建任务使用
  新端点，避免下载中途无记录地切换线路。
- 代理诊断格式新增 `failure=...`；主进程日志记录 failed generation、恢复次数、恢复结果、新 generation、
  新端口和健康状态；播放器报告的 runtime 诊断也包含同一故障类型和 generation 变化。主文档 WebView
  网络错误、动态 OkHttp 连接错误和浏览器下载重试/耗尽错误进入同一脱敏日志窗口；ProxyController 安装
  与清理回调等待 5 秒后会明确失败，不会永久占用协调锁。
- 多段下载对 Range 分段严格要求 HTTP `206`。服务端忽略 Range 返回 `200` 时明确失败，避免完整响应被
  写入某一个分段文件后生成静默损坏的下载文件。

### 本轮验证与待验证

- `:app:compileDebugKotlin` 与 `KiyoriMihomoRuntimeProcessPolicyTest`、`KiyoriNetworkProxyLogStoreTest`
  定向测试通过。
- 真实设备仍需验证：核心自然退出后 Browser WebView、活动 mpv、Browser 下载和 AI 多段下载的行为，
  以及恢复失败限额、外部 VPN/Clash 并存、前后台切换和进程重建。设备验证前本专项保持
  `verification_pending`。

## 2026-08-24 最终收口与交付候选

### 本轮实现

- 运行中 Mihomo 每 `10s` 执行一次本地 Controller/mixed-port 健康检查，连续两次失败才停止旧
  generation 并进入现有每 generation 一次、五分钟最多两次的自动恢复；主动停止、启动失败和上游节点
  超时不进入该触发器。
- Browser WebView 主文档错误、动态 OkHttp 路由连接错误、浏览器下载重试/耗尽错误和 Mihomo 核心尾行
  都写入同一个 1000 条脱敏日志窗口；导出继续保留 process/PID、generation、端口、健康状态和 stop
  reason。ProxyController 安装/清理回调增加 5 秒超时。
- `HttpMultiPartDownloader` 对 Range 分段严格要求 HTTP `206`，避免服务器忽略 Range 返回完整 `200`
  时造成静默分段损坏；Browser 下载保留任务级 route snapshot，恢复完成后的新任务使用新端点。

### 本地验证

- `:app:testDebugUnitTest --no-daemon --console=plain`：299 个 XML 报告、1750 个测试，
  `0 failures / 0 errors / 0 skipped`。
- `check_formal_readiness.py --repository . --require-main`：PASS；
  `check_architecture_boundaries.py --repository . --require-main --phase auto`：PASS；
  `git diff --check`：PASS。
- `:app:assembleDebug --no-daemon --console=plain`：PASS，235 actionable tasks，
  Mihomo parent-death、脚本代理 runtime、播放器 runtime packaging 和唯一 Debug launcher 门禁通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`493116749` bytes，SHA-256
  `EC4B0B4D25571B2CF4F27F7F4C97FC83B6A442C2944F16EEB27FC703FE4E3C1C`；包身份为
  `com.kiyori / 45 / 0.1.0`，仅 `arm64-v8a`，Debug V2 单 signer 与 16 KB `zipalign` 通过。

### 待验证边界

本轮未安装 APK、未操作 ADB/设备、未使用真实订阅复测，未进行外部 Clash/VPN 并存、网络切换、前后台
重建或 Mihomo 人为自然退出注入。真机仍需确认健康看护实际触发、WebView 新端口生效、活动 mpv 刷新、
下载任务冻结端点的可观测失败行为和真实上游速度；在这些证据完成前，本专项保持 `verification_pending`。
