# 应用网络路由契约

适用于 AI、工具、Browser、下载、播放器、脚本及应用在线服务。用户配置见 [网络与媒体指南](../../user-guide/network_and_media.md)，详细设计与现场证据见 [应用代理专项](../../TODO/application_network_proxy/index.md)。

## 单一配置与路由

- 主进程 `KiyoriNetworkProxyManager` 持有加密 schema-5 配置与唯一 Mihomo。`AI_SERVICES / AI_TOOLS / BROWSER / DOWNLOADS / PLAYER / SCRIPTS / APP_SERVICES` 统一遵循 `RULE / GLOBAL / DIRECT`。
- `RULE` 在保留的订阅规则前应用自定义匹配；`GLOBAL` 使用 `KIYORI_APP_PROXY`；`DIRECT` 只绕过应用内代理，仍可能经过 Android 系统 VPN。
- 仅传统 JsEngine 脚本可按已安装包设置 `INHERIT / DIRECT / PROXY`，包含停用但可执行的包；ToolPkg 属于 `AI_TOOLS`，不冒充传统脚本。
- 当前网站显式禁用代理对该完整域名及其子域名具有最高应用代理优先级，不绕过系统 VPN。
- 加密配置跨进程读取时同时比较文件 metadata 与加密头/随机 IV，避免播放器进程读到旧保存结果。
- 下载任务在 HEAD、Range、分段和重试之间冻结一个路由快照；URLConnection 与 OkHttp 使用相同的 loopback/私网旁路规则。

## 订阅与进程边界

- 导入边界明确分类单文档 UTF-8 Clash/Mihomo YAML mapping、逐行 URI 节点列表，以及标准或 URL-safe Base64 编码的 URI 列表。URI 当前转换 VLESS、Hysteria2/Hy2、Trojan 和无插件 Shadowsocks；未知协议或字段计入“不支持”，无效凭据、端口与编码计入“拒绝”，零可用节点不保存。摘要保存输入格式、可用、隔离、拒绝和不支持数量。
- VLESS 仅保留可验证的 TCP、WebSocket、gRPC、TLS 与 Reality 字段；Hysteria2 保留 SNI、证书跳过、证书指纹、Salamander 混淆和端口跳跃。未知字段不静默丢弃。YAML 继续拒绝重复键、无有效出站和非法 provider。
- 订阅 URL、清洗后的 YAML 和控制器密钥由 Android Keystore 加密，保存在 no-backup 私有目录。
- 内嵌核心只监听随机 loopback mixed-port，不启用 TUN、LAN 入站或订阅 Controller。依赖外部 GeoSite/GeoIP/ASN 或远程 rule-provider 的规则被明确计数排除，校验不在代理启动前下载数据库。
- RULE 中命中 DIRECT 仍由核心转发 CONNECT/TCP，核心退出会中断该连接；它不同于模块 DIRECT 绕过内置核心。
- 内嵌核心从固定版本源码构建，输入与验证见 [Mihomo 构建契约](../../../tools/mihomo_runtime/README.md)。
  Linux splice 的读、写 `EINTR` 只继续原系统调用，保留当前字节位置，不终止连接或重发应用请求；
  其他传输错误仍由原路径处理。每条复制流各方向最多记录一次 EINTR 处理日志。
  非 splice 原始 TCP `read` / UDP `recvmsg` 同样在 EINTR 后继续原调用，不向上返回伪连接失败；
  EAGAIN、EOF、关闭和超时保留原语义。核心转发结束按方向记录本机客户端端口和受控原因分类，
  `copy_complete` 不等于 AI 协议完成，不记录目标地址、正文或异常原文。
- launcher 的 Linux `PDEATHSIG` 绑定创建子进程的父线程。主核心、probe 与配置校验均由专用创建线程启动，
  该线程等待子进程退出，避免协程 worker 回收误停核心；既有 runtime 保持唯一 Process 与停止所有权。
  创建/等待线程中断不代表停止授权，启动异常原样返回，宿主进程死亡仍由 launcher 终止核心。
- 设置页临时订阅探测在 IO 块内交还资源句柄；取消跨调度器返回或测速后，仍以不可取消清理
  关闭该探测进程并清除私有目录。取消保持取消语义，不冒充订阅下载失败或主核心错误。
- 自定义规则独立保存，订阅更新只替换订阅规则；不覆盖自定义规则。
- 默认拒绝与外部系统 VPN 并存；用户明确允许后，内嵌核心的出站不再嵌套进系统 VPN，
  路径为应用 → Kiyori Mihomo → 应用内出站底座 → 物理网络 → 节点。模块 `DIRECT` 不受该开关影响，
  仍可能经过系统 VPN。
- 出站底座是主进程内的回环 SOCKS5 中继，与核心进程同生共死，随机端口且只接受随机用户名口令；
  监听地址固定为运行配置写给核心的 `127.0.0.1`，不使用 `InetAddress.getLoopbackAddress()`
  （Android 上是 `::1`，核心只会得到 connection refused）。启动阶段应用先自己完成一次握手，
  不可达时明确失败而不是让核心带着不可用的解析器运行；接受循环意外结束会关闭监听，
  使后续连接立即被拒绝而不是挂在 backlog 里。
  匿名握手被拒绝，因此同设备其它应用不能借用该端口。它在运行配置里以保留名 `KIYORI-VPN-BYPASS`
  出现，订阅的代理、代理组与代理集合都不能占用该名字；它是基础设施，不作为可选节点出现在节点列表。
- 底座把每条出站套接字绑定到当前“非 VPN”的物理 Network（已验证优先，其次以太网 > Wi-Fi > 蜂窝），
  域名也在绑定网络上解析，因此系统 VPN 的 DNS 劫持不会让核心拿到外层的 fake-ip。
  网络切换由 callback 更新，不重启核心；VPN 锁定导致绑定连接被拒绝时退回系统默认路由并记录，
  不静默失败。CONNECT 与 UDP ASSOCIATE 都经过底座，基于 UDP 传输的节点不因此失效。
- 并存模式下核心使用固定的明文解析器并强制经过底座（同一地址各提供 UDP 与 TCP 一条路径），
  不保留订阅自带的 DoH 与 `fake-ip`：DoH 需要一次隧道内的引导解析，正是要避免的输入。
  该模式是 runtime 指纹的一部分，切换开关会重建核心而不是复用旧进程。
- 首次 URL 导入在没有 active subscription 时使用 Android 系统网络，因此可以由系统 VPN 提供可达性；已有活动订阅且需要内嵌代理时，更新必须使用当前 Kiyori 路由，失败不会静默改走直连。外部 Clash 只开放本机 mixed-port 而未接入 Android VPN/TUN 时，Kiyori 不扫描或自动接管该端口。

## 启动与恢复

- manager 持有单一 readiness state 与 generation，不能永久缓存第一次成功或失败。
- 每次请求复用只核对主核心进程存活和配置身份；不执行短超时 Controller/端口探测，
  不因某个请求的一次控制面超时停止所有连接。启动就绪检查与既有后台连续健康检查继续生效，
  请求本身的连接失败原样返回，不自动重发；日志中的健康字段仍是最近探测快照。
- `enabled=true` 只允许在当前订阅存在、清洗配置非空且具有 root route 时持久化；从关闭切到开启、导入、替换、切换和编辑订阅均在保存前用包含订阅规则与当前自定义规则的 RULE runtime 执行 Mihomo `-t`。
- 系统网络 callback 合并网络增删及能力变化，在既有 `mutationMutex` 内重读最新配置与全部网络的 VPN transport 后协调。VPN 冲突停止核心并清理 WebView 覆盖；不创建第二核心或静默直连。
- 首个真实 WebView 完成 provider、能力和脚本桥初始化后安装进程代理覆盖；就绪前阻止远程主文档，包括恢复、Back、Forward、Refresh。`about:`、`file:` 等本地文档不等待。
- 协调失败使导航明确失败；之后成功配置会发布新 generation，允许后续导航。
- `UNEXPECTED_PROCESS_EXIT` 与连续两次 `HEALTH_CHECK_FAILED` 进入同一恢复协调；显式关闭、VPN 冲突、配置替换和启动失败不冒充该触发器。
- 在既有 `mutationMutex` 中重读配置与 VPN；每失败 generation 一次、五分钟最多两次。限额后保持 `ERROR`，不换节点、不静默直连。
- 核心退出立即使旧 WebView 端点失效。相同健康端点不重复安装覆盖；generation 失败清除缓存。代理关闭后的协调可正常完成，过期 deferred 不能写回新状态。
- ProxyController 安装与清理回调有五秒超时；恢复只影响新媒体请求，既有下载保留原任务路由证据。
- 动态 OkHttp 客户端发送请求前复核已复用连接的实际 proxy；若配置或 runtime endpoint 已变化，
  当前 exchange 明确失败且不发送请求。不能直接关闭池化 socket，因为 HTTP/2 上可能仍有其他
  流；先标记该连接不再接收新 exchange，再由 OkHttp 在已有流释放后关闭连接，避免旧连接
  持续被选中。`OkHttpConnectionRetirement` 隔离固定 OkHttp 4.12.0 的内部 ABI 调用：公共
  `evictAll` 只处理空闲连接，不能完成此操作。升级依赖必须通过真实 HTTP/2 并发流与新连接回归。

## 播放器传输与诊断

- 主进程 PlayerSession 每个 HTTP(S) 请求解析一次 `PLAYER` 路由，并持有 `PlayerMediaStreamBridge`。桥接只监听 `127.0.0.1`，覆盖 mpv `http-proxy` 无法代理 HTTPS 的路径。
- `:player` 只接收已解析目标与 transport ID，不创建代理 manager、不观察或修改 Mihomo；系统网络快照中 `proxy=absent` 不等于应用路由直连。
- 分段下载必须收到 `206`；服务端忽略 Range 返回 `200` 时明确失败，不能把完整响应写入分段文件。
- 主进程日志记录 generation、就绪、端口健康、耗时、核心尾行和明确停止原因；播放器进程只记录被动网络与 native 证据。
- 核心 INFO 日志记录 TCP 实际命中的规则及出站链；请求失败同时记录 `callCancelled` 和 runtime phase。
  最后一次端口健康快照不能证明停机后的核心仍运行，`route=RULE` 也不能代替实际 DIRECT/节点命中证据。
- 动态 OkHttp 路由将收到响应头前和读取正文时的失败分别记录为 `REQUEST_OR_HEADERS`、`RESPONSE_BODY`，
  模式标签固定为请求开始时的配置。正文观察不预读、不重试，保留原超时与异常，同一正文读取/关闭失败只记一次；
  正常 EOF 仍由协议层判断是否语义完成，代理层不伪造完成事件。
- 日志保留最近 1000 条进程内记录，导出标明进程/PID；URL 凭据、路径、Bearer、secret/password/token、UUID 和私有路径先脱敏。
- 同类控制台事件在一秒窗口合并但保留 `repeatCount`，不得把重复数当成丢弃数。具体 TLS、HTTP、端口和网络错误仍是独立事实。

## 验证边界

自然退出的触发来源、真实订阅、HTTPS/Range/HLS 和长期稳定性以专项设备证据为准。运行时重新启动不能证明上游网络质量问题已经解决。
