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

- 只接受单文档 UTF-8 YAML mapping，使用 `Clash.Meta` 请求身份；拒绝 Base64 节点列表、重复键、无有效出站和非法 provider。
- 订阅 URL、清洗后的 YAML 和控制器密钥由 Android Keystore 加密，保存在 no-backup 私有目录。
- 内嵌核心只监听随机 loopback mixed-port，不启用 TUN、LAN 入站或订阅 Controller。依赖外部 GeoSite/GeoIP/ASN 或远程 rule-provider 的规则被明确计数排除，校验不在代理启动前下载数据库。
- 自定义规则独立保存，订阅更新只替换订阅规则；不覆盖自定义规则。
- 默认拒绝与外部系统 VPN 并存；用户明确允许后路径为应用 Mihomo → 系统 VPN → 节点。

## 启动与恢复

- manager 持有单一 readiness state 与 generation，不能永久缓存第一次成功或失败。
- 首个真实 WebView 完成 provider、能力和脚本桥初始化后安装进程代理覆盖；就绪前阻止远程主文档，包括恢复、Back、Forward、Refresh。`about:`、`file:` 等本地文档不等待。
- 协调失败使导航明确失败；之后成功配置会发布新 generation，允许后续导航。
- `UNEXPECTED_PROCESS_EXIT` 与连续两次 `HEALTH_CHECK_FAILED` 进入同一恢复协调；显式关闭、VPN 冲突、配置替换和启动失败不冒充该触发器。
- 在既有 `mutationMutex` 中重读配置与 VPN；每失败 generation 一次、五分钟最多两次。限额后保持 `ERROR`，不换节点、不静默直连。
- 核心退出立即使旧 WebView 端点失效。相同健康端点不重复安装覆盖；generation 失败清除缓存。代理关闭后的协调可正常完成，过期 deferred 不能写回新状态。
- ProxyController 安装与清理回调有五秒超时；恢复只影响新媒体请求，既有下载保留原任务路由证据。

## 播放器传输与诊断

- 主进程 PlayerSession 每个 HTTP(S) 请求解析一次 `PLAYER` 路由，并持有 `PlayerMediaStreamBridge`。桥接只监听 `127.0.0.1`，覆盖 mpv `http-proxy` 无法代理 HTTPS 的路径。
- `:player` 只接收已解析目标与 transport ID，不创建代理 manager、不观察或修改 Mihomo；系统网络快照中 `proxy=absent` 不等于应用路由直连。
- 分段下载必须收到 `206`；服务端忽略 Range 返回 `200` 时明确失败，不能把完整响应写入分段文件。
- 主进程日志记录 generation、就绪、端口健康、耗时、核心尾行和明确停止原因；播放器进程只记录被动网络与 native 证据。
- 日志保留最近 1000 条进程内记录，导出标明进程/PID；URL 凭据、路径、Bearer、secret/password/token、UUID 和私有路径先脱敏。
- 同类控制台事件在一秒窗口合并但保留 `repeatCount`，不得把重复数当成丢弃数。具体 TLS、HTTP、端口和网络错误仍是独立事实。

## 验证边界

自然退出的触发来源、真实订阅、HTTPS/Range/HLS 和长期稳定性以专项设备证据为准。运行时重新启动不能证明上游网络质量问题已经解决。
