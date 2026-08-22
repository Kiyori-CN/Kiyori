---
fork: https://github.com/Kiyori-CN/Kiyori
status: in_progress
baseline: 485cf3f7a8a1c2e5ccf0d525baf960ae8398f9b5
date: 2026-08-23
---

# Kiyori 应用级网络代理与内嵌 Mihomo

## 目标与完成标准

本阶段把上一版“传统脚本宿主代理”提升为 Kiyori 唯一的应用级网络路由能力。用户只在
“设置首页 -> 更多功能 -> 网络代理”管理订阅、内嵌核心、默认连接模式、逐模块连接模式和脚本
细分规则。Browser、播放器、AI 主模型与其他由 Kiyori 持有的联网客户端读取同一份配置，不再
由脚本页面持有第二套代理状态。

完成标准：

1. Clash/Mihomo 订阅可以通过 URL 或本地 YAML 导入到加密订阅库；订阅库支持多条保存、刷新
   状态、远端更新、编辑、复制、切换和删除。服务端按客户端类型返回 Clash YAML 时必须获得
   mapping 根，而不是 Base64 通用 URI 列表。
2. 每份订阅独立保留安全、可用的 `proxy-groups`、静态节点、HTTP provider、策略选择和最近测速
   结果；页面按订阅和策略组展示节点，可执行单节点和整组测速。
3. 内嵌核心关闭时，Kiyori 在应用层不设置代理；开启时，默认连接模式和逐模块规则决定哪些
   请求进入内嵌 Mihomo。脚本模块继续允许按脚本包名细分。
4. AI 服务、AI 工具、Browser、下载器、播放器、脚本与扩展、Kiyori 在线服务的受支持宿主
   网络入口全部接入同一个路由 owner；代理错误必须终止当前请求，不得静默改走另一条路线。
5. 当前订阅是唯一运行配置来源。切换、更新或编辑 URL 完成下载、清洗和 `mihomo -t` 校验后才
   原子保存；运行应用失败必须明确区分于保存失败，不自动切回旧订阅。
6. 外部 Clash/VPN 与内嵌 Mihomo 的关系有确定、可测试的行为；“直连”只表示不经过 Kiyori
   Mihomo，不能承诺绕过 Android 系统 VPN。
7. 新页面复用 Kiyori 设置视觉和唯一 route stack；脚本顶栏设置按钮直接打开环境变量抽屉，
   抽屉底部左侧“网络代理”按钮进入同一个设置子页面。
8. 敏感配置、运行时明文、核心制品和私有订阅都满足现有安全、打包、许可证和仓库卫生门禁。

## 当前实施状态

- `IN PROGRESS`：真机首次保存确认 Android Keystore 拒绝调用方提供的 GCM IV；单订阅 schema、
  无作用域导入反馈和错误的组测速端点正在重构为多订阅库与节点探测模型。
- `DONE`：AI 主模型/语音/embedding、AI 工具、传统脚本、Browser、下载、播放器、Coil 图片和
  Kiyori 在线服务入口已接入统一网络模块；ToolPkg 与传统脚本身份边界已固定。
- `IN PROGRESS`：设置入口和模块路由已接入；订阅 URL/YAML 导入、多订阅操作、分组节点、单项/
  批量测速、作用域进度与错误反馈正在同一页面 owner 内完成。
- `REGRESSION CONFIRMED`：用户已安装上一份 Debug APK；URL/YAML 导入和总开关因首次加密保存
  失败而不可用。此前本地 JVM/构建通过不能替代 Android Keystore 真机语义。
- `verification_pending`：修订 APK 尚未在真机验证；真实订阅、多订阅切换、WebView、AI、播放器、
  下载器、脚本、外部 VPN 并存和进程生命周期矩阵仍待设备验收。

## 非目标

- 不启用 Android `VpnService`、Mihomo TUN、LAN listener、透明代理或设备全局代理。
- 不代理 Kiyori 之外的应用，不修改系统 Wi-Fi 代理，不接管其他应用的 Clash 配置。
- 不承诺改变 Terminal、用户自行启动的进程或远端 MCP 进程内部自行创建的 socket；只有 Kiyori
  宿主持有并能明确归类的网络入口进入本合同。
- 不把私有订阅 URL、订阅响应、节点密钥、Controller secret 或本地运行配置写入 Git、日志、
  崩溃报告、任务日记或普通偏好文件。
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
| `KiyoriNetworkProxyManager` | 路由解析、系统 VPN 门禁、OkHttp/WebView/mpv 适配和状态投影 |

上一版 `ScriptNetworkConfigStore`、`ScriptProxyRuntime`、`ScriptNetworkHttpClientFactory` 与
`ScriptNetworkSettingsScreen` 不再作为兼容入口保留。Kiyori 尚无公开发行渠道，本轮直接建立
schema v1 的应用级配置，不读取或迁移本机开发阶段的 `script_network` 私有目录。

## 持久化模型

```text
KiyoriNetworkProxyConfig(schemaVersion = 2)
├── enabled: Boolean = false
├── defaultMode: DIRECT | PROXY
├── moduleModes: Map<NetworkModule, INHERIT | DIRECT | PROXY>
├── scriptModes: Map<packageName, INHERIT | DIRECT | PROXY>
├── subscriptions: List<KiyoriProxySubscription>
│   ├── id / displayName / sourceType
│   ├── url // URL source only
│   ├── sanitizedYaml
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
override。任何 `PROXY` 路由都要求当前订阅有效、根策略已选择、系统 VPN 门禁通过且核心处于
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
| 复制地址 | 二次提示链接可能包含凭据，再由用户明确写入系统剪贴板 |
| 创建副本 | 复制加密内容到新 ID 和新名称，不切换当前订阅 |
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
| `DOWNLOADS` | Browser 文件下载、模型/扩展资产下载 | 下载任务创建时读取当前路由 |
| `PLAYER` | mpv HTTP/HTTPS、HLS/DASH、字幕与封面 | 在 mpv 初始化前设置 `http-proxy` |
| `SCRIPTS` | 传统 `JsEngine` 脚本包的标准宿主网络 | 支持逐 package override |
| `APP_SERVICES` | GitHub、市场、天气、规则订阅、Coil 网络图片 | Kiyori 自身在线能力 |

WebView 的 AndroidX `ProxyController` 是进程级 API，不能把不同 WebView 可靠拆成多个代理模式；
所有远程 WebView 因此归入 `BROWSER`。本地 `127.0.0.1`、`localhost` 与应用内页面必须旁路。
播放器由独立 mpv 网络栈持有，不能依赖 Java `ProxySelector`。OkHttp 与 `HttpURLConnection`
分别使用 manager 提供的动态 selector/显式 `Proxy`，不设置 JVM 全局 system property。

## Mihomo 配置与策略组

### 输入边界

- 只接受单文档、严格 UTF-8、最大 4 MiB 的 YAML mapping。
- 禁止重复 key、递归 key、非标量 key、过量 alias 与超过 64 层的结构。
- 删除订阅提供的所有 inbound、TUN、LAN、sniffer、Controller、secret、profile 路径和 listener。
- 静态节点必须有唯一名称、类型和安全的 server；本地/私有字面地址条目被隔离并计数。
- provider 只允许 `http` 类型和绝对 HTTP(S) URL；provider 本地 path 改写到应用私有目录。
- DNS 可以保留出站解析配置，但删除 `listen`；不允许订阅开启本机 DNS listener。

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
- 日志只保留阶段、错误码、计数和脱敏 host；不得记录订阅 URL path/query、YAML、节点、密钥、
  代理认证、Controller secret 或响应正文。

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
青色网络语义图标和 `26dp` 底部选择面板，不创建卡片套卡片。结构从上到下固定为：

1. **代理状态**：应用内代理 `Switch`、当前阶段、当前订阅与默认策略；右侧“刷新状态”只协调
   runtime 和 Controller。启动、停止和刷新期间按钮尺寸固定，不让进度状态改变布局。
2. **订阅库**：顶部“当前订阅”打开可搜索单选抽屉；“添加订阅地址”和“导入 YAML”是独立
   命令。其后按保存顺序显示订阅行，包含名称、脱敏来源、使用中状态、更新时间、流量/到期、
   节点/组/隔离计数。点击条目打开动作抽屉，提供更新或重新导入、编辑、复制地址、创建副本、
   切换和删除；删除二次确认。
3. **默认连接**：使用“直连 / 代理”两段 segmented control；代理开关关闭时仍可编辑并保存策略。
4. **模块连接**：七个模块使用统一行和当前值；点击后以底部面板选择“跟随默认 / 直连 / 代理”。
   每行图标与内容区域尺寸稳定，长模块名允许换行。
5. **策略组与节点**：只展示当前选中的订阅，根组显示为“默认代理”，其余按订阅顺序展示。
   手动组打开可搜索单选面板；组行提供整组测速。节点抽屉按分组筛选，显示类型、最近延迟、
   成功/超时/失败和单节点测速按钮；provider 动态节点由 Controller 探测后进入列表。
6. **脚本规则**：进入可搜索底部抽屉，逐脚本选择“跟随脚本模块 / 直连 / 代理”。ToolPkg 不
   伪装成传统脚本；ToolPkg 宿主 HTTP 归入 `AI_TOOLS`。
7. **高级**：局域网地址是否进入代理、系统 VPN 并存授权、测试 URL、重置代理设置。重置必须
   二次确认并停止核心，删除加密配置和运行明文。

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

页面显示本地化、可执行的短消息；日志保留错误码和异常类型。不得把原始英文异常直接作为主 UI，
也不得在失败后自动修改总开关、模块模式、节点或测试 URL。

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
10. [IN PROGRESS] 审计精确候选树、敏感内容、私有订阅、缓存、大文件、子模块和远端竞争，提交并推送
    `main`，核对 local/tracking/remote ref。
11. [PENDING] 在目标设备完成真实订阅、多订阅切换/测速、外部 VPN 并存、Browser、AI、播放器、
   下载器、脚本和进程
   生命周期验收。

## 自动验证矩阵

- 模型策略：总开关、默认模式、逐模块、逐脚本、VPN 门禁、局域网地址策略。
- YAML：目标服务同类 Clash YAML、Base64 格式拒绝、占位节点隔离、策略组引用清理、重复名、
  provider path、单文档、UTF-8、4 MiB、alias、深度和 secret 不出现在异常。
- Runtime：随机 loopback port、无 LAN/TUN、Controller bearer、组快照/切换、旧明文清理、核心
  异常退出、parent-death、配置指纹变化和停机幂等。
- 网络入口：AI 主模型与模型列表、普通 AI 工具、传统脚本四入口、Browser WebView、Browser
  下载、mpv option、Kiyori 服务 client 均使用明确 module；本地 URL 始终旁路。
- UI/导航：More Features 顺序、NETWORK_PROXY Back 链、环境变量按钮直接开抽屉、底部左按钮、
  订阅空/有数据/加载/错误、策略组长列表、浅深主题、横屏和窗口尺寸。
- 仓库/APK：现有 Mihomo 固定 SHA、Gradle 下载/ELF/16 KB 检查、无订阅泄漏、无运行明文或大
  ELF 提交、Debug APK identity/signer/zipalign/native basename。

## 真机验收

1. 外部 Clash 关闭：从本地 YAML 首次导入，开启 Kiyori 代理，Google、DuckDuckGo、Brave 与
   非 Search 脚本可用。
2. 外部 Clash 临时开启：使用 URL 首次导入目标订阅，确认策略组、节点、流量摘要和占位隔离数；
   关闭外部 Clash 后由 Kiyori 独立联网并更新订阅。
3. Browser 打开被阻断网站并测试登录、Cookie、WebSocket、下载、无痕和本地页面；直连模式与
   代理模式按模块切换。
4. AI 主模型完成普通与流式响应，语音/embedding 按 AI 模块路由；AI 工具和脚本按各自模块路由。
5. mpv 播放 HTTP/HTTPS、HLS/DASH 与字幕，切换视频、前后台和服务重建后仍使用快照模式。
6. 系统 VPN 存在且未授权时内嵌核心明确拒绝；授权后验证双层链路；直连模块仍经过系统 VPN。
7. 旋转、分屏、前后台、进程强杀、订阅更新失败、核心异常退出、网络切换和重启后状态一致。

自动检查和 Debug APK 不能代替这些设备证据；设备完成前状态保持 `verification_pending`。
